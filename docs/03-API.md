# REST API

Base URL: `http://localhost:8087/api`. The production UI is served by the same Spring Boot application. Development Vite proxies `/api` to the backend. The API requires a signed-in account and CSRF tokens for writes; see the authentication section below.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | /health | App, shard, Redis, Qdrant and AI-configuration status; health observation may be cached for five seconds |
| POST | /queries | Validate and queue a query; returns 202 with UUID |
| GET | /queries/{id} | Durable status, error and result |
| GET | /queries | Latest 50 jobs, newest first |
| POST | /documents | Multipart file plus optional productId/customerId; returns saved document with 202 |
| GET | /documents | Latest 100 documents |
| POST | /documents/{id}/retry | Retry a FAILED or WAITING_FOR_KEY document with a configured key |
| GET | /documents/{id}/download | Download the original file as an attachment |

## AI query

```json
{"question":"Find customers who purchased laptops above ₹50,000 and summarize their uploaded product reviews.","mode":"HYBRID"}
```

Modes: AUTO, SQL, RAG, HYBRID. Questions are 3–2000 characters. If AI is unconfigured, requests without an explicit guided plan return 503 with a useful message. A 202 response means queued, not completed.

## Guided SQL query

```json
{
  "question":"Explore laptop purchases above ₹50,000",
  "mode":"SQL",
  "plan":{
    "mode":"SQL","intent":"PURCHASES","category":"laptop",
    "minPrice":50000,"customerId":null,"productId":null,
    "limit":50,"explanation":"Explicit filters chosen in the SQL explorer."
  }
}
```

Explicit plans are only allowed in SQL mode. Valid intents: PURCHASES, CUSTOMERS, PRODUCTS, REVIEWS, INVENTORY. The limit is 1–100. Filter combinations that have no meaning for an intent are rejected rather than ignored. There is no endpoint accepting raw SQL.

## Result semantics
`status` is QUEUED, RUNNING, COMPLETE, PARTIAL or FAILED. A COMPLETE query may legitimately contain zero rows. PARTIAL covers unavailable sources, truncated selections, missing documents or failed AI synthesis. A failed job stores a sanitized error.

`result` includes mode, answer, rows, sources, traces, warnings, validated plan, cached flag, original generation timestamp, duration and answerMethod. `GEMINI` means the configured provider generated the summary; `DETERMINISTIC_SQL`, `NO_EVIDENCE` and `EVIDENCE_ONLY` are explicit non-generated alternatives.

Sources include document UUID, filename, excerpt number, text, similarity and association metadata. Similarity is a vector-search score, not factual confidence. SQL traces include shard, bucket, parameterized SQL, bound parameters, fetched row count, time and execution status. Row counts in traces may include one extra look-ahead row used to detect truncation.

Cache hits preserve their original generation timestamp and old trace timings; duration reports the current request's processing time. Sample-source cache TTL is 45 seconds. Personal-table queries bypass the cache. There is no CDC invalidation for external business-data changes.

## Upload and retry
Use multipart field `file` with PDF/DOCX/TXT. Optional numeric fields: `productId`, `customerId`. Maximum 10 MB, 100 PDF pages, 200,000 extracted characters and 100 chunks. Empty/scanned or invalid files fail. Uploads without a key are saved as WAITING_FOR_KEY; after configuring and restarting, explicitly retry them.

Document statuses: WAITING_FOR_KEY, QUEUED, PROCESSING, READY, FAILED. Processing retries up to three attempts. Kafka publication retries through the durable pending row. READY documents with the currently configured embedding model are eligible for retrieval.

## Errors and limits
400 invalid input; 401 sign-in required; 403 CSRF/disallowed browser origin; 404 unknown UUID; 409 conflict/workspace cap; 413 upload too large; 429 request or queue limit; 502 provider response failure; 503 missing configuration/unavailable dependency; 504 provider timeout. Async processing failures appear in the job/document status endpoint rather than changing the earlier 202.

There is a 60-write-request/minute application-local limit and a bounded query worker queue. These are demonstration safeguards, not distributed abuse prevention. Errors have `{status, message}`. No provider secrets or raw SQL exception details are returned.

Import `postman/ShardQuery.postman_collection.json` into Postman. The machine-readable source contract is `docs/openapi.json`; the collection adds assertions and should be updated with it.

## Authentication and personal databases

All data endpoints require the `SHARDQUERY_SESSION` cookie. Start with `GET /auth/csrf` using a cookie jar, then include the returned token in `X-CSRF-TOKEN` on every POST/PUT (including register/login/logout). After successful authentication, refresh the token because the session ID and CSRF state rotate. The React client handles this automatically.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | /auth/csrf | Public token endpoint; establishes anonymous session |
| POST | /auth/register | `{name,email,password}`; 201 account; automatically signs in |
| POST | /auth/login | `{email,password}`; 200 account |
| GET | /auth/me | `{id,name,email}` for signed-in account |
| POST | /auth/logout | 204; invalidate session and clear cookie |
| GET / POST | /databases | List own databases / create with `{name}`; creation returns 201 |
| POST | /databases/{database}/retry | Retry failed provisioning |
| GET | /databases/tables | List all tables in own databases for the query selector |
| GET / POST | /databases/{database}/tables | List / create table; creation returns 201 |
| GET / POST | /databases/{database}/tables/{table}/rows | Read page / insert record; insertion returns 201 |
| PUT | /databases/{database}/tables/{table}/rows/{row} | Replace all values of an existing row; 200 |

Create a table:
```json
{"name":"Students","columns":[{"name":"student_name","type":"TEXT","required":true},{"name":"marks","type":"INTEGER","required":true}]}
```
Insert/update:
```json
{"values":{"student_name":"Asha","marks":"92"}}
```
Read `?page=0` (zero-based) to get `{rows,total,page,pageSize}`. Page size is 50. Each row includes `_row_id`; text, booleans, nulls and date strings retain their types. Integer/decimal user fields are returned as strings to avoid JavaScript precision loss. PUT replaces all defined fields, so omitted optional fields become null.

Query a personal table:
```json
{"question":"List students with marks above 80","mode":"SQL","tableId":"REPLACE_WITH_OWN_TABLE_UUID"}
```
An explicit tableId does not accept `plan`; Gemini produces a bounded filter plan that the backend validates. Response plan intent is `CUSTOM_TABLE` (not accepted as a client-provided guided-plan intent). Personal-table answers are not cached. Supported operations and resource caps are in [the user guide](11-ACCOUNTS-AND-DATABASES.md).

401 means sign-in is required; 403 means invalid CSRF or disallowed origin; 404 also covers a resource belonging to another account; 409 covers duplicate names or resource limits. The global in-process write cap is 60/minute; register/login additionally share 15 attempts per IP per minute. These limits reset with the process and do not provide distributed rate limiting. No invitation token is required.

The Postman collection starts with CSRF, login and CSRF refresh. Set `email` and `password` locally to an existing account and keep the cookie jar enabled. Its database example creates a database/table/row in that account, consuming one of the three database slots; do not repeatedly run that mutation block without understanding the cap. The service exposes no destructive database/table API in this version.

## Automatic personal-data routing (v1.2)

`source` defaults to `AUTO`. The backend inspects the signed-in owner’s table catalog before choosing personal data, demo commerce, or documents. Set `PERSONAL` to exclude demo records, or `DEMO` to explicitly choose them. Omit `tableId` to allow a validated inner equality join between two owned tables, even in different personal databases. For example: `{"question":"Find students whose fees are above ₹150000","mode":"AUTO","source":"PERSONAL"}`.

Personal-table queries now support one two-table inner join in addition to typed filtering/sorting. `tableId` still restricts a request to one table. Results include `dataSource`, `tables`, `requestSource` and optional `requestTableId`; these restore the source correctly in History. AUTO and PERSONAL bypass the answer cache. See [routing and bounds](12-PERSONAL-QUERY-ROUTING.md).

## Database management and connections

See [the management contract and connection setup](13-DATABASE-MANAGEMENT-AND-CONNECTIONS.md). OpenAPI 1.3.0 includes rename, confirmed delete, column editing, connection creation/update and schema refresh. All mutations require the session CSRF header and account ownership. Remote data is read-only; deleting a connection only disconnects it.
