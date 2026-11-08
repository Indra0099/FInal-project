# Architecture and scope

ShardQuery AI is one React JavaScript frontend and one Java 21 / Spring Boot modular monolith. PostgreSQL stores durable business records and application metadata. Kafka handles document ingestion. Qdrant stores embeddings. Redis caches complete query responses, job progress and brief health observations.

## Important distinction: domains and hash buckets
The three independent PostgreSQL databases are domain shards, not interchangeable horizontal copies. Customer rows belong to `shard_customer_db`, order rows to `shard_order_db`, and products/reviews to `shard_product_db`. Application-side association joins identifiers across them. There are no cross-server foreign keys or distributed transactions.

Within customer and order databases, customer-related tables use three logical partitions, with `bucket = floorMod(Integer.hashCode(customer_id), 3)` for supported positive 32-bit customer IDs (therefore `customer_id % 3`). Explicit bucket predicates demonstrate hash routing and partition pruning. These buckets are not additional physical database servers. Product lookup uses its domain shard.

This simulates distributed database behavior for a college demonstration. It is not a production distributed SQL cluster, consensus system or arbitrary federated query optimizer.

## Modules
| Module | Responsibility |
| --- | --- |
| web | Validated endpoints, origin checks, bounded requests, error responses |
| query | Typed plans, approved SQL templates, routing, bounded cross-shard association |
| ai | Gemini JSON planning, embeddings and evidence-based answer generation |
| document | PDF/DOCX/TXT extraction, chunking, ingestion and cited retrieval |
| persistence | JPA documents, durable query jobs and ingestion outbox |
| infrastructure | Three read-only pools, Kafka listener, Redis cache, Qdrant REST |

## Query contract before implementation
`POST /api/queries` accepts `question` (3–2000 chars), `mode` (`AUTO`, `SQL`, `RAG`, `HYBRID`) and an optional `plan` for the explicit guided SQL explorer. A job ID is returned immediately. Poll `GET /api/queries/{id}` for durable status and results. AI planning returns a constrained intent (`PURCHASES`, `CUSTOMERS`, `PRODUCTS`, `REVIEWS`, `INVENTORY`) with typed filters. The server compiles parameters into approved SQL; it never executes model-produced raw SQL.

Responses contain an answer, actual database rows, source excerpts, executed SQL templates and parameters, routing information, warnings and duration. Partial results are explicit and not cached. Hybrid retrieval is filtered to product/customer associations from SQL. If no SQL records match, unrelated documents must not be substituted.

## Document contract
Upload PDF, DOCX or UTF-8 TXT through `POST /api/documents`, with optional positive `productId` and `customerId` metadata. Files are bounded to 10 MB, extracted text to 200,000 characters and at most 100 chunks. Relating a review to the corresponding product/customer enables precise hybrid retrieval. Uploaded text is untrusted evidence, never system instructions.

The metadata row and pending outbox state are persisted together. A scheduled dispatcher publishes a document ID to Kafka; the listener extracts, embeds and upserts stable chunk IDs into Qdrant. Duplicate delivery is safe through deterministic IDs and READY checks. Retries are bounded and visible; failed documents can be retried explicitly. PostgreSQL is the source of truth for state, even if Redis is unavailable.

## UI
Four destinations: Ask, Documents, History and How it works. The main page has a question box, three example prompts and compact answer/results/sources tabs. SQL details stay in an expandable section. No enterprise control panel or unnecessary charts.

## Local demonstration boundary
Personal accounts use BCrypt passwords, HttpOnly sessions and CSRF protection. Documents, history and personal databases are scoped to account ownership. Shared fictional commerce data remains read-only. Docker publishes on loopback; public deployment still requires TLS and operational hardening. API keys remain server-side. See [account/database design](11-ACCOUNTS-AND-DATABASES.md).

## Failure behavior
Shard queries have connection and statement deadlines. A dependent unavailable shard prevents a complete join; incomplete information is labelled. Redis outage reduces caching only. Kafka outages retain pending ingestion work. Gemini absence permits the real guided SQL explorer, while AI planning, embeddings and RAG report that configuration is required. Test fixtures are never presented as live AI.

See the UML and sequence diagrams in `diagrams/`, the API contract, and the implementation log for exact behavior and tested limits.

The product PostgreSQL instance also hosts application metadata. If that entire instance fails, new/persisted workflows are unavailable; the API reports a service error. Partial-join handling can still be demonstrated by stopping the customer or order database. Splitting metadata into a fourth database is a future resilience extension.
