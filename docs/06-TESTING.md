# Testing and reproducibility

## Unit tests
```sh
cd backend
mvn test
```
JUnit 5 and Mockito cover plan bounds, unsupported filters, hash routing, parameter binding, shard-failure warnings, truncation, TXT validation, actual PDF/DOCX extraction and overlapping chunks. InfrastructureIntegrationTest is skipped unless SQ_INTEGRATION=1. A skip is not a pass of that integration scenario.

## Real infrastructure with a simulated AI provider
Start the six infrastructure services. Install the Postman CLI if you want this test route. Its local simulation does not require a signed-in Postman account.

From the project root, terminal one:
```sh
postman simulation run postman/simulations/openai.sim.yaml --port 4017 --no-history --output ndjson
```
Terminal two, macOS/Linux:
```sh
cd backend
SQ_INTEGRATION=1 mvn test
```
Windows PowerShell:
```powershell
cd backend
$env:SQ_INTEGRATION='1'
mvn test
Remove-Item Env:SQ_INTEGRATION
```
The tests use the real PostgreSQL, Kafka, Redis and Qdrant services, an isolated Qdrant collection, temporary upload paths and recorded fixture UUIDs. Test metadata is cleaned by UUID. Use a fresh test workspace, or first ensure there are no pending/processing document jobs. The test dispatcher must not process real pending documents using fixture embeddings. Stop the ordinary application while running these tests: they share the metadata database and Kafka consumer group, and startup recovery assumes a single application instance.

The simulation stands in only for Gemini HTTP endpoints. Its deterministic embeddings test request shapes, ingestion and metadata filtering, **not semantic relevance or real model quality**. Its responses say “contract-test.” They are never enabled by the normal application or Docker configuration.

Integration checks include cross-shard matching, real database write rejection, API validation/not-found handling, provider 429 handling, duplicate document ingestion, Kafka processing, hybrid association filtering and citation validation with a bounded format-correction attempt. Inspect Surefire reports under `backend/target/surefire-reports/` and the simulation's request events. A mocked request passing does not establish live Gemini account access.

## Postman API tests
Import `postman/ShardQuery.postman_collection.json`, set baseUrl to `http://localhost:8087` and run the collection against the real application. Or:
```sh
postman collection run postman/ShardQuery.postman_collection.json --no-report-events
```
The collection queues a guided query, polls its state, validates the four expected seed matches and checks history, validation errors and unknown identifiers. It does not need a Gemini key. Run against the unchanged fictional seed data.

## Frontend build
```sh
cd frontend
npm ci
npm run build
```
Browser checks should cover empty/loading/error states, explicit SQL execution, result tabs, history navigation, document upload, source downloads and mobile layout. Actual checked screens are listed in `VERIFICATION.md`.

## Failure demonstration
Only stop this project's service, and restart it afterwards:
```sh
docker compose stop customer-db
# Run a new guided purchase query; use a different customer filter to avoid a cached response.
docker compose start customer-db
```
Expected: database rows may lack customer attributes, warnings explain the incomplete association, status is PARTIAL and the result is not newly cached. A complete result cached before an outage remains a previous observation until TTL expiry; its timestamp and cache label are displayed.

Redis downtime must not destroy history. Kafka downtime must leave documents pending. Product-database downtime also removes the metadata store, so the app cannot promise durable query execution then. Tests should distinguish these failure boundaries.

## Live AI acceptance
With your own configured key, upload the fictional sample files, wait for READY, run one RAG and one hybrid question, and inspect their citations and associations. Verify assertions against the actual evidence. Provider latency, cost, answer accuracy and semantic retrieval quality cannot be inferred from the contract simulation. Record only measurements you actually take.

## Accounts and user databases
The same integration suite now tests real HTTP registration/login/logout and CSRF rotation, BCrypt storage, session ID rotation, owner-scoped history/document download/retry, RAG exclusion of another account’s otherwise-identical document, database/table/row creation, type validation, quoted SQL values, malformed identifier rejection and owner-only table queries. It creates unique fixture accounts and a temporary real PostgreSQL database, then removes only those fixture resources. Enable the `shard_workspace` role first using the start helper or `docker compose exec -T product-db bash -s < infra/enable-workspaces.sh`. No personal Gemini key is read by the simulated tests.

## Database management and connections

`DatabaseConnectionTest` covers encryption, URI/host checks and identifier quoting. `InfrastructureIntegrationTest` covers lifecycle, account/CSRF boundaries, read-only remote access, source preservation after disconnect, schema refresh, UUID predicates and a filtered query over 1,005 source records. See [verification](VERIFICATION.md) for the 50-test result and separate live Gemini/browser observations. The optional `postman/Database-Management.postman_collection.json` is a manual examples collection; supply only disposable IDs and a private local connection variable.

## Frontend deletion regression tests

Run `cd frontend && npm ci && npm test` with Node 20.19+ or 22.12+. The seven tests in `tests/deletion.test.mjs` mount the actual React components in JSDOM, simulate HTTP through an Axios adapter and verify confirmation, cancellation, retry and list state. They never connect to live databases. `docker compose build app` also runs them before bundling the frontend. Actual endpoint deletion is verified separately against disposable PostgreSQL data; see VERIFICATION.md.
