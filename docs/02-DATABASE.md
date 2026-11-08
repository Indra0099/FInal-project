# Database design and query routing

## Domain placement
| Database | Tables | Responsibility |
| --- | --- | --- |
| shard_customer_db | customers, customer_addresses | Customer profiles and addresses |
| shard_order_db | orders, order_items, payments | Delivered purchases and payment records |
| shard_product_db | products, inventory, customer_reviews | Catalogue, stock and structured reviews |
| app schema in shard_product_db | documents, query_jobs | JPA metadata and durable workflow status |

SQL initialization is in `infra/customer.sql`, `infra/order.sql`, `infra/product.sql` and `infra/init-db.sh`. Compose runs these on a fresh database volume. They are the version-1 schema baseline; this project does not silently modify existing schemas on startup (`ddl-auto=validate`). Future schema changes require explicit, reviewed SQL migrations.

## Hash buckets
For supported positive 32-bit customer IDs:

```java
int bucket = Math.floorMod(Integer.hashCode(customerId), 3);
```

101 → 2, 102 → 0, 103 → 1. PostgreSQL LIST partitions use this computed bucket. The check constraint `bucket = customer_id % 3` keeps writes consistent with the Java router. Customer-specific query templates include both the customer ID and bucket. The PostgreSQL planner can prune irrelevant partitions.

Do not call these three logical buckets three extra servers. Domain placement selects the server; the customer hash selects a local partition. This resolves the difference between the requested domain databases and customer-based routing without pretending their schemas are interchangeable.

## Cross-shard purchase query
1. Product DB: select up to 200 eligible product identifiers by category/product ID.
2. Order DB: select delivered line items for those identifiers, applying historical `unit_price > minPrice`, customer ID/bucket and a maximum result count.
3. Customer DB: look up only the customers from those rows, with a bucket predicate when one customer was selected.
4. Java associates rows by IDs and keeps missing customer attributes as unavailable. Warnings preserve incomplete results.
5. Hybrid retrieval derives exact product/customer pairs from the returned rows. It never broadens an empty SQL result into a search over unrelated documents.

The minimum price is **strictly greater than**, denominated in INR. It is not an order-total filter. Cancelled orders are excluded from PURCHASES. Aggregation here means assembling results, not SQL SUM/COUNT/AVG support.

## Constraints and indexes
- Composite primary keys `(bucket, customer_id)` and `(bucket, order_id)` support partitioned tables.
- Local foreign keys enforce customer-address, order-item/payment and product-inventory/review relationships.
- Customer/product references crossing databases are logical relationships, not enforced cross-server foreign keys.
- Price, quantity, rating and bucket constraints reject invalid seed/business values.
- Indexes support category lookup, product line-item lookup and product/customer review lookup.
- Unique document fingerprints prevent duplicate ingestion of the same file, metadata and model.
- Query history and outbox indexes support bounded recent-history and pending-dispatch reads.

## Privileges
`shard_admin` initializes the databases. The query engine uses `query_reader`, granted SELECT only, with `default_transaction_read_only=on`, a four-second statement deadline and a bounded pool. JPA uses `shard_app`, which owns only the `app` schema in the product database. Credentials come from environment variables.

## Persistence and consistency
Business records, job history, document metadata, files, Kafka logs and Qdrant points use Docker volumes. Redis is disposable. Outbox state is represented by a document's QUEUED status plus next_dispatch; it is saved before Kafka publication. Delivery can repeat. Stable point IDs and READY-state checks avoid duplicate indexed chunks. This is at-least-once processing, not an exactly-once transaction across PostgreSQL, Kafka and Qdrant.

A simultaneous update across different databases can produce a mixed-time result. There is no distributed snapshot or cross-database transaction. The demonstration seed dataset is static unless deliberately changed by the developer.

## Account and personal database metadata

Flyway migration `backend/src/main/resources/db/migration/V1__accounts_and_user_databases.sql` extends the original schema with `app.users`, `app.user_databases`, `app.user_tables` and nullable user foreign keys on documents/query jobs. The migration is additive: existing records remain unassigned, not erased or automatically claimed. User email, user/database-name pairs and database/table-name pairs are unique. Owner/status and owner/created-time indexes support private lists and retrieval.

Each personal database is an actual database on the product PostgreSQL instance, named `sq_` plus a generated UUID. Each user table has its own physical table name. `_row_id` is an identity primary key; approved columns use varchar(4000), bigint, numeric(18,4), boolean or date. Metadata stores the display names and column definitions for the editor. Backend account checks control every operation. This is application-level ownership over a shared restricted PostgreSQL role, not per-user database logins.

The workspace role can create databases but cannot administer roles or act as superuser. Private database CONNECT is revoked from PUBLIC. Row operations bind parameters, cap counts and close JDBC connections. See [the editor guide](11-ACCOUNTS-AND-DATABASES.md) for quotas, setup, limitations and data persistence.
