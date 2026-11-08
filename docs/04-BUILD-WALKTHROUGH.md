# How the project was built, step by step

This guide explains the implementation in its dependency order. Commands assume the project root unless stated otherwise. Read the corresponding files while following each step.

## Step 1 — Define scope before choosing code
Read `00-REQUIREMENTS.md` and `01-ARCHITECTURE.md`. Separate structured facts, document evidence and combined questions. Choose one modular backend and keep only five explainable SQL intents. Define partial results and unavailable AI behavior before writing the UI.

## Step 2 — Create the three databases
Read `infra/*.sql` and `infra/init-db.sh`. Define primary keys, local foreign keys, checks and useful indexes. Create customer/order bucket partitions. Add clearly fictional example records. Create separate read-only query and metadata-write credentials. Start infrastructure with:

```sh
docker compose up -d customer-db order-db product-db kafka redis qdrant
```

Inspect `docker compose ps`. Initialization scripts run only for new volumes. Never use `down -v` as a casual fix; it deletes project data.

## Step 3 — Build the Spring Boot foundation
`backend/pom.xml` pins Spring Boot 3.5.16 and Java 21. The application uses Web, Validation, JDBC, JPA, Kafka and Redis. PDFBox and POI extract documents. `application.yml` reads credentials and service addresses from environment variables. JPA validates the explicit metadata schema.

## Step 4 — Implement a typed query contract
`QueryTypes.java` defines modes, intents, plans, SQL traces, document sources and final results. `Plan.validated()` rejects unsupported combinations and unsafe bounds. This contract connects the UI, planner and execution engine. A validated JSON plan is not executable code.

## Step 5 — Connect and route shards
`ShardManager.java` creates a bounded read-only Hikari pool for each domain database. `bucket(customerId)` selects a logical partition. All query connections have deadlines. Health checks report unavailable nodes without pretending they returned zero matching rows.

## Step 6 — Compile safe SQL
`SqlEngine.java` chooses static query templates and binds user/model values through NamedParameterJdbcTemplate. It performs the product → order → customer association, records execution traces and warns on truncation or missing dependencies. It never passes arbitrary model strings to a SQL execution API.

Run the SQL-related JUnit tests with `cd backend` then `mvn test`. They check binding, routing, invalid plans and partial failures. Infrastructure tests also exercise real PostgreSQL roles and joins.

## Step 7 — Add AI at explicit boundaries
`GeminiGateway.java` implements Gemini REST calls with strict output schemas, deadlines and one bounded retry for transient failures. Planning only chooses from the approved intent vocabulary. Embeddings use 1536 dimensions. Answer generation sees only the bounded rows and selected excerpts. Citation identifiers are checked against retrieved sources. AI output still needs human verification; these controls do not establish semantic correctness.

## Step 8 — Extract and index documents
`DocumentExtractor.java` verifies supported formats, uses PDFBox/POI, rejects unreadable/oversized text and chunks with overlap. `DocumentService.java` persists file and metadata, fingerprints duplicates, dispatches pending IDs to Kafka and consumes them. Stable Qdrant IDs make repeated upserts idempotent. Three failed processing attempts lead to a visible FAILED state.

The upload filename is display metadata only. Stored paths are generated UUIDs. Docx decompression uses POI's zip-bomb protections. PDFs are limited by size/pages; malicious-document isolation is still a production hardening requirement.

## Step 9 — Connect evidence to records
`VectorStore.java` stores text, document ID, chunk index, filename, model and product/customer metadata. RAG searches ready documents. Hybrid search adds exact product/customer pair filters. This is essential: a similar review from another customer must not be presented as a matched customer's review.

## Step 10 — Persist and coordinate query jobs
`QueryService.java` creates durable PostgreSQL jobs, executes them on a bounded worker pool and writes complete/partial/failed results. Redis is a cache, not the source of truth. Query keys include the request, model and ready-document revisions. On restart, interrupted queries are marked failed with a rerun instruction.

## Step 11 — Expose clear HTTP endpoints
`ApiController.java`, `ErrorHandler.java` and `LocalGuard.java` validate requests, restrict browser origins, apply a simple request limit and avoid exposing internal/provider errors. Uploads and query submissions return 202; the UI polls durable status. See `03-API.md`.

## Step 12 — Build the React JavaScript interface
`frontend/src/App.jsx` contains the four small screens. Axios handles API calls, React Router handles hash-based navigation and ordinary React state drives forms/polling. `styles.css` supplies the cream/forest palette, responsive layout and accessible focus states alongside Tailwind. The SQL explorer is explicitly separate from AI-generated answers.

## Step 13 — Test the integrated system
Run JUnit/Mockito tests, real PostgreSQL/Kafka/Redis/Qdrant integration tests with a test-only Gemini HTTP simulation, and the Postman API collection. Then test the production interface in a browser. Distinguish simulated-provider checks from a real-key demonstration. See `06-TESTING.md` and `VERIFICATION.md`.

## Step 14 — Package as one application
The Dockerfile builds React, copies the static assets into Spring Boot and packages the JAR. The runtime image runs as a non-root user. Docker Compose supplies the six infrastructure containers and persistent volumes. There is no separate frontend server in the final deployment.

## Step 15 — Prepare the demonstration and report
Use fictional documents from `samples/`. Keep real query results and source excerpts visible during the viva. Explain the domain/hash distinction and limitations honestly. Use the diagrams in `UML-AND-WORKFLOWS.md` and the presentation flow in `07-DEMO-AND-VIVA.md`.

## Account and database editor extension
Follow [the implementation walkthrough](11-ACCOUNTS-AND-DATABASES.md#8-implementation-walkthrough) for the Flyway migration, session authentication, owner-scoped repositories, database provisioning, table/row editor, validated Gemini table plans and frontend integration. This extension preserves the existing sample-shard and document-processing modules.
