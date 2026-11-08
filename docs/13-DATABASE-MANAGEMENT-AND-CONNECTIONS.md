# Database management and PostgreSQL connections

## Contract

Owned local databases support renaming and confirmed deletion. Tables support renaming, adding optional columns, renaming columns, removing columns with confirmation, deleting tables and deleting individual rows. Existing row editing remains available. Type conversion is deliberately excluded to avoid silently damaging existing values. Required columns can be added only to empty tables. Destructive API requests require the current object name (or row ID) in `confirmation` and retain normal authentication, CSRF and ownership checks. The UI displays the selected name and sends it only when the user explicitly clicks Delete permanently; typing the name is no longer required.

External PostgreSQL connections use a standard `postgresql://user:password@host:port/database?sslmode=verify-full` link and a selected schema, default `public`. Credentials are encrypted with AES-GCM using an installation key stored outside source code on the persistent upload volume. API responses and AI prompts never include connection links, passwords or ciphertext. Only allowlisted hosts may be connected. No arbitrary JDBC properties, system schemas, views or non-PostgreSQL drivers are accepted.

The connection imports schema metadata, not a copy of rows. Queries read live tables in read-only transactions. External sources cannot be written or dropped from this application; disconnect removes local connection metadata only. A dedicated SELECT-only PostgreSQL role is required by setup instructions. Table metadata can be refreshed after remote schema changes. Unsupported column types are excluded and reported; no supported table means an explicit error.

Connected tables participate in the same owner-scoped catalog as local tables. Single-table filters and sorting execute as bounded parameterized SELECT statements. Two-table association (including different connections) remains bounded to 1,000 rows per source and 5,000 joined rows; exceeding a bound returns a limitation rather than an incomplete answer. No general SQL, aggregates, outer joins or more than two tables. Gemini receives selected schema and matching records when answering, never credentials.

## API additions

- `PATCH /api/databases/{id}`: `{name}`; rename local database or connection label.
- `DELETE /api/databases/{id}`: `{confirmation}`; delete local database or disconnect external source.
- `POST /api/databases/connect`: `{name, connectionUrl, schema}`; validate, connect and discover tables.
- `PUT /api/databases/{id}/connection`: `{name, connectionUrl, schema}`; replace saved connection after successful validation.
- `POST /api/databases/{id}/refresh`: discover current remote tables/columns.
- `PATCH /api/databases/{id}/tables/{table}`: `{name}`; rename local table.
- `DELETE /api/databases/{id}/tables/{table}`: `{confirmation}`; delete local table.
- `POST /api/databases/{id}/tables/{table}/columns`: `{name,type,required}`.
- `PATCH /api/databases/{id}/tables/{table}/columns/{column}`: `{name}`.
- `DELETE /api/databases/{id}/tables/{table}/columns/{column}`: `{confirmation}`.
- `DELETE /api/databases/{id}/tables/{table}/rows/{row}`: `{confirmation}`.

Write operations on external tables return 409. Other-account resources return 404. Validation errors return 400, missing confirmation/conflicting names 409, unavailable connections 503. No destructive migration of existing data is part of this feature.

## Use the database controls

1. Open **My databases** and select a local database.
2. Choose **Rename** to change its display name. Physical storage identifiers remain stable, so existing records stay intact.
3. Select a table. Use **Rename table**, **Edit columns**, **Add row**, or the row's **Edit** action.
4. Under Edit columns, add an optional field, rename a field, or remove a field. Removing a field deletes its values permanently; data-type conversion is not provided. To make a new field required, start with an empty table.
5. Use **Delete** for a row, **Delete table**, or **Delete database**. The dialog displays the selected name/row ID and explains what will be removed. Choose **Delete permanently** to confirm, or **Cancel** to keep it. You do not need to type a name. After success the item is removed from the list immediately. Deletion has no undo; export important records first. Existing saved query snapshots are retained in History and can contain earlier values.
6. A database that has an active query may refuse deletion. Wait for the query to finish and retry; the app does not terminate active connections.

## Connect another PostgreSQL database, step by step

1. Obtain permission from the database owner and the database hostname, port, database name, schema and SELECT-only credentials. This release supports PostgreSQL, not MySQL or MongoDB.
2. On that server, an administrator can create a restricted login and grant access to selected tables. Replace the names and password below; run this in the intended database, never against an unrelated system:

   ```sql
   CREATE ROLE shardquery_reader LOGIN PASSWORD 'replace-with-a-strong-unique-password';
   GRANT CONNECT ON DATABASE college TO shardquery_reader;
   GRANT USAGE ON SCHEMA public TO shardquery_reader;
   GRANT SELECT ON TABLE public.students, public.student_fees TO shardquery_reader;
   ```

   Do not grant CREATE, INSERT, UPDATE, DELETE or superuser privileges. New tables need additional SELECT grants. Row-level security on the source still applies.

3. In ShardQuery's local `.env`, add the exact server hostname to the comma-separated allowlist. Do not put the URL or password in this setting:

   ```dotenv
   EXTERNAL_DB_ALLOWED_HOSTS=localhost,127.0.0.1,host.docker.internal,db.example.com
   ```

4. Restart the app with `docker compose up -d --force-recreate app`. With native Java development, restart the Spring Boot process with the environment setting. No new build is needed for host configuration.
5. In **My databases → Connect PostgreSQL**, enter a friendly connection name, schema (`public` by default), and the link:

   ```text
   postgresql://shardquery_reader:ENCODED_PASSWORD@db.example.com:5432/college?sslmode=verify-full
   ```

   Percent-encode reserved characters in the user/password, such as `@` as `%40`. Only the `sslmode` URL option is accepted. The default `verify-full` verifies the certificate and hostname using the Java trust store; `require` encrypts but does not validate the server certificate. Use it only if that matches your provider's configuration. These semantics follow the [PostgreSQL JDBC connection documentation](https://jdbc.postgresql.org/documentation/use/) and [TLS documentation](https://jdbc.postgresql.org/documentation/ssl/).
6. Choose **Connect and discover tables**. The app checks access before saving anything. Only ordinary/partitioned tables with SELECT access are discovered. Views, foreign tables and system/ShardQuery metadata schemas are excluded. Up to five connections per account and 50 tables per connection are supported.
7. Open a discovered table to preview records, then choose **Ask about this table**. Alternatively, open Query workspace and use **Automatically choose** or **My databases — all tables**. Mention the connection/table name when multiple sources have similar schemas.
8. For two related tables in different connected databases, include their relationship in the question if it is ambiguous. For example: “Join students.id to student_fees.student_id and find students whose fees exceed 150000.” The answer shows which tables supplied the records.
9. After a remote schema change, choose **Refresh schema**. This retains IDs for existing tables, adds newly accessible tables and removes missing tables from the local catalog. It does not change remote rows. Supported types are text, UUID, integers, numeric values, booleans and dates; unsupported columns are reported in the connection notice.
10. Use **Update connection** to replace the link/credentials. The previous link is not shown or prefilled, and a failed connection attempt leaves the old configuration intact.
11. Use **Disconnect** to forget the local connection. This deletes only its saved encrypted credentials and imported schema metadata, never the remote database or rows. Query History retains prior answer snapshots.

### Local database and Docker notes

`localhost` inside the application container refers to that container. For PostgreSQL running directly on the computer, use `host.docker.internal`; the source server must listen on an address reachable from Docker and allow the restricted login in `pg_hba.conf`. On Linux, Compose supplies the host-gateway mapping. A PostgreSQL container on the same Docker network can be reached using its service name, which must be explicitly added to the allowlist. Do not open the source database to the public internet just for this demo. `sslmode=disable` is accepted only for named local development hosts, not remote hostnames.

### Storage, limits and troubleshooting

- Passwords and complete links are never returned by an API, printed in application errors or included in model prompts. The backend stores AES-256-GCM ciphertext in `app.user_databases.connection_secret`. Its randomly generated installation key is in `<UPLOAD_DIR>/.secrets/database-connections.key`, with owner-only permissions on POSIX systems. Back up this key together with the metadata database and persistent upload volume. Losing the key requires re-entering every saved connection. Do not commit it to source control.
- Connections are subject to connect/read/statement timeouts. Remote queries use read-only transactions even if an accidentally overprivileged login is supplied; always use SELECT-only credentials as a separate protection.
- A single-table query pushes validated filters and ordering into SQL and returns at most 100 rows. A browser preview shows at most 1,000 rows and labels truncation. Two-table joins read at most 1,000 rows per source and fail explicitly if the limit is exceeded; narrow/prepare the source data or query tables individually for larger joins.
- “Host is not enabled”: add the exact host to the allowlist and restart. Wildcards and arbitrary JDBC properties are not supported.
- “Could not read”: check network routing, source credentials/grants, TLS trust and the schema. Secrets are intentionally omitted from the error.
- “No readable tables”: check SELECT and schema USAGE grants, the selected schema, and supported data types.
- DDL in the local workspace spans a metadata database and a separate user database. It uses transactions and retryable/idempotent deletion, but is not a distributed atomic transaction. A process crash at commit boundaries can require metadata recovery from backups. This is a bounded educational application.

## Implementation map

`V2__external_database_connections.sql` adds source kind and encrypted connection metadata without rewriting existing records. `DatabaseManagementService` validates confirmations and performs local DDL/row deletion. `PostgresConnectionSpec` parses a strict URI and checks the host allowlist. `ConnectionSecrets` manages installation encryption. `ExternalDatabaseService` discovers schemas, retains stable table IDs and opens read-only connections. `WorkspaceService` routes both local and connected sources through the existing validated query engine. `DatabaseActions.jsx` provides confirmation dialogs and connection forms; `DatabaseStudio.jsx` exposes the controls. API contracts are in `openapi.json`; JUnit/infrastructure tests exercise real PostgreSQL with a labelled Gemini simulation. Live verification is recorded separately in `VERIFICATION.md`.
