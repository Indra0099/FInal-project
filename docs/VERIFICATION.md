# Verification record

## Deletion confirmation usability fix — 8 November 2026

The reported deletion problem could not be reproduced at the API: both a populated table and a database containing a populated table were deleted successfully using the packaged application. The visible UI did keep Delete permanently disabled until an exact name was typed. The updated dialog shows the selected item's name with a clear final confirmation button and Cancel; it no longer requires text entry. No deletion is sent merely by opening the dialog. The API still receives and checks the current name, ownership and CSRF token.

After a successful response, database/table entries and the selected editor are updated immediately, followed by a server refresh. Errors leave the dialog open for retry, and duplicate submits are blocked while a request is pending.

- **Seven new frontend component tests passed** using Node's test runner, React and JSDOM with an Axios test adapter. They cover table/database confirmation payloads and CSRF, cancellation, failure/retry, duplicate submits, list removal and preservation of unrelated entries. These are component tests with simulated HTTP, not browser or database execution.
- The same seven tests also passed inside the Docker frontend build. The Java package step passed **36 unit tests**, with 14 opt-in infrastructure tests skipped; the earlier full 50-test integration result remains recorded below.
- **Firefox inspection:** the rebuilt dialog displayed the selected disposable database name, an enabled final Delete permanently button without a text-entry gate, and Cancel. Cancel left the fixture intact. See [confirmation screenshot](screenshots/delete-confirmation.png). Actual destructive HTTP checks and component-click tests are recorded separately.
- **Actual localhost HTTP checks after rebuilding:** deleting a populated table returned 204 and an empty table list; deleting a database containing another populated table returned 204 and an empty database list. These checks used a disposable account only and did not alter the user's databases.

## Database management and external PostgreSQL — 8 November 2026

Implemented local database/table renaming; confirmed database, table, column and row deletion; optional/empty-table-required column addition; column renaming; and encrypted PostgreSQL connection creation/update, discovery, preview, refresh and disconnect. The additive V2 migration leaves existing local databases marked LOCAL.

- **50 tests, zero failures, errors or skips:** 36 unit tests plus 14 integration methods using real PostgreSQL, Redis, Kafka and Qdrant with a labelled local Gemini HTTP simulation. New tests cover encrypted credentials and key reopening, ciphertext binding to connection IDs, strict URI options, allowlisted hosts, UUID filters, correct quoting, confirmed deletion, ownership/CSRF rejection, record preservation, unsupported required-column changes, read-only transactions, schema refresh preserving table IDs, failed connection updates preserving credentials, and disconnect leaving source rows intact.
- A real connected fixture with **1,005 rows** returned the correct filtered/sorted 50-row SQL result. Its browser preview reported truncation at 1,000. This tests SQL pushdown, not an unbounded two-table join or production performance.
- **Live Gemini:** two disposable source databases were connected through a SELECT-only PostgreSQL role. The student/fees question joined IDs across both connections and returned only fictional Asha Rao with fees 220000; the equality-boundary record at 150000 was excluded. Both source names and two real reads appear in [the actual result](sample-connected-query-result.json). The fixture host was temporarily allowlisted for verification and removed from the running configuration afterward.
- **Firefox:** verified local database renaming, optional-column addition with the original row preserved, the disabled deletion button without a matching confirmation, cancellation without deletion, connected table preview without edit controls, the connection form, and the saved Gemini answer with both connected sources. See [management controls](screenshots/database-management.png) and [connected result](screenshots/connected-database-answer.png). A CSS class collision discovered during inspection was corrected and rechecked in the rebuilt app.
- **Packaged HTTP cleanup:** local database deletion and external disconnect returned successful responses. Both external source tables still contained their two original rows after disconnect. Only then were the disposable source databases/role/account removed through test teardown. The user's current three tables across two databases retained their pre-update counts and content hashes.
- React compilation and the final Docker build succeeded. All **17 Mermaid sources** parse. OpenAPI internal references and both Postman collection JSON files validate. The new manual management collection contains examples and was not executed as a sequential collection; API evidence comes from the integration/HTTP tests above.

No internet-hosted database credentials were supplied by the user. Live external-connection verification used independent PostgreSQL databases on this installation through actual connection URLs, not a public cloud provider or WAN network. Public-host TLS/firewall configuration remains deployment-specific. Type conversions, unsupported imported column types and general federated SQL remain outside scope. The app is running on localhost:8087, with Gemini retained.

## Automatic routing for personal databases — 8 November 2026

Fixed the default query path, which previously sent questions without an explicit table ID to the commerce-only planner. The updated planner receives only the current account's table catalog and selects validated personal tables, sample commerce data, or documents. Personal queries support one table or one inner equality join between two related tables, including different personal databases. This remains a bounded college demonstration, not general federated SQL.

- **43 backend tests passed, zero failures, errors or skips:** 32 unit tests and 11 integration methods. Integration used real PostgreSQL, Redis, Kafka and Qdrant with a labelled local Gemini HTTP simulation. New checks cover automatic student/fee routing, numeric filtering, join-before-limit behavior, reserved field names, unknown/other-account tables, null join keys and row-expansion limits.
- **Actual Gemini:** the exact question “find the students in database whose fees is above ₹150000” automatically selected two disposable personal tables. It matched student ID S1 to a fee row with a different row ID F10 and returned only fictional Asha Rao with fees 220000. The student at exactly 150000 was excluded. Two actual SQL reads were recorded; the result was uncached and COMPLETE. See [live fictional result](sample-student-fees-result.json).
- **Commerce regression with personal schemas present:** an automatic laptop-price query still selected DEMO and returned three sample products. No provider simulation was used for these live checks.
- **Firefox end-to-end:** refreshed the packaged app, signed into the disposable account, verified the automatic source and two-table catalog, entered the student-fees question, submitted it, and inspected the completed answer and one-row Records tab. Both table names appeared under Data used. See [browser result](screenshots/student-fees-answer.png). Signed out after verification.
- **Preservation:** the user's original two tables retained their pre-change record counts and content hashes. Only disposable verification databases/accounts/jobs were removed; no original user records were changed.
- **Packaging:** React production compilation and Docker application build passed. All 15 Mermaid files parse. The OpenAPI and Postman collection JSON parse, and all local OpenAPI references resolve. The rebuilt app is running at localhost:8087.

Model interpretation can still vary. Unsupported aggregates, more than two selected tables, ambiguous joins and personal SQL-plus-document combinations produce a limitation/error rather than invented data. The tests establish these scenarios and bounds, not accuracy for every natural-language question. Existing browser tabs must be refreshed to load the new source selector.

## Account and database extension — 8 November 2026

The updated full suite completed with **35 tests, zero failures, zero errors and zero skips**: 25 unit tests plus 10 integration methods. The integration run used real PostgreSQL, Kafka, Redis and Qdrant with a local, explicitly labelled Gemini HTTP simulation. It verified:

- Real HTTP registration, password hashing, duplicate/weak credential rejection, login/logout, session rotation and CSRF-token renewal.
- Account-scoped document reads/download/retry and query history; another account receives 404. Identical uploaded content belongs separately to each account and other-account documents are excluded from hybrid retrieval.
- Creation of an actual PostgreSQL database/table, typed row insertion and editing, required/type checks, SQL-looking text stored as data, invalid identifiers rejected, and owner-only table access.
- A structured personal-table AI plan compiled into a parameterized query and summary through the simulated provider. This establishes caller/infrastructure behavior, not live model accuracy.

The additive Flyway migration ran against the existing installation without deleting prior documents or query jobs. Disposable integration accounts and the integration database were cleaned up. The updated OpenAPI file has 17 paths with valid internal references; all 14 Mermaid diagram sources parse successfully.

Firefox verification completed on the packaged localhost application: registration form, existing-account sign-in, database creation, two table definitions, row insertion, row editing, private history, saved-query source restoration and logout. A 390 × 844 responsive viewport was inspected; this is not physical-device testing. A real Gemini query over two fictional records returned only Asha with the updated score of 95, without warnings. The final query preserved its requested limit of 3 and its table ID. See [observed JSON](sample-personal-table-result.json), [database screenshot](screenshots/my-databases.png), [mobile screenshot](screenshots/my-databases-mobile.png) and [saved query screenshot](screenshots/personal-table-query.png). Disposable UI data and account were removed after verification. The Docker package passed 25 unit tests; 10 infrastructure tests were intentionally skipped inside the image build and passed separately in the full suite. Earlier verification sections describe the previous anonymous version and its sample results; those legacy records are now preserved but hidden from new accounts.


Date: **8 November 2026**. Local macOS development environment, Java 21, Maven, Node.js and an isolated Colima Docker profile named `shardquery`. Application URL: `http://localhost:8087`. Fictional sample business data only.

## Frontend redesign — latest UI verification

The application now uses midnight blue, cobalt and orange, horizontal navigation, a two-column query workbench and a split upload/library screen. Vite and Docker builds passed. Firefox showed the packaged redesign on port 8087. A fresh query submitted from the new editor completed with three laptop products and no warnings; history reopening, records, document status, hybrid source excerpts, the guide route and the skip link were checked. The desktop and 390 × 844 responsive layouts were visually inspected. See [the design walkthrough and screenshots](10-FRONTEND-DESIGN.md). The mobile screenshot is from the Vite preview; the desktop screenshot is from the rebuilt application. No new upload or physical-phone test was performed for this visual change. Backend code and API contracts are unchanged.

## Gemini provider update — latest verification

At the user's request, paid OpenAI access was replaced with Gemini's OpenAI-compatible REST API. The default chat model is `gemini-3.1-flash-lite`; embeddings use `gemini-embedding-001`, normalized to 1536 dimensions. Only `GEMINI_API_KEY` is read by the live app. There is no paid-provider fallback.

The final suite ran with `SQ_INTEGRATION=1`: **32 tests, 0 failures, 0 errors, 0 skipped** (25 unit tests and 7 real-infrastructure integration tests). It covers Gemini embedding envelopes, reduction/normalization, bounded HTTP retries, citation correction and source validation. The simulation recorded three embedding calls, six successful chat responses and two 429 responses. The React build and packaged Docker application build passed.

A personal Google AI Studio project displayed **Free tier** when checked. Its existing key is configured only in the ignored local `.env`; it is not included in the source package. No billing was enabled. The application's health endpoint confirms the key's presence, not account billing status.

### Actual Gemini checks

These were live Google requests using fictional sample data, not simulated answers:

| Check | Observed result |
| --- | --- |
| Catalogue SQL question | COMPLETE, PRODUCTS plan, three laptop products priced above ₹50,000 |
| Document indexing | Five supplied documents READY after actual Gemini embedding generation and Kafka/Qdrant processing |
| Return-policy question | COMPLETE, seven-calendar-day return window and required support review, with citations to the policy |
| Combined purchases and reviews | COMPLETE, four delivered purchase records and four review sources with matching customer/product pairs |
| Browser verification | Firefox displayed the completed Gemini answer, four records and four source links; the missing-key banner was absent |

See [actual live query responses](sample-gemini-results.json) and [the Gemini result screenshot](screenshots/gemini-hybrid-answer.png). These checks establish the demonstrated examples, not general answer accuracy or production capacity.

### Issues found and corrected

The real embedding endpoint omitted the optional `index` field, unlike the original fixture. The parser now accepts entirely positional or entirely indexed batches and rejects mixed/duplicate/incomplete responses. A two-item live batch matched the order of individual embedding requests. Both native 3072-value and reduced 1536-value vectors are normalized for storage.

An early document answer returned PARTIAL because its citation list did not match its inline references. The answer prompt now specifies the exact reference format. The validator accepts grouped references such as `[D1, D2]`, preserves their identities, and still rejects unknown, missing or mismatched citations. One bounded correction uses the original evidence; an invalid answer is never shown as successful. Regression tests verify correction and persistent rejection. Subsequent live RAG and hybrid checks both completed without warnings. This validates reference identity, not the truth of each generated claim.

The earlier 22-test provider update and 25-test embedding fix preceded the final 32-test run. The records below preserve initial-version verification history. Initial screenshots show the earlier OpenAI setup wording; the latest screenshot shows the real Gemini answer.

## Executed checks

| Check | Observed result | Scope |
| --- | --- | --- |
| Initial full Java suite with SQ_INTEGRATION=1 | 17 tests, 0 failures, 0 errors, 0 skipped | 11 unit + 6 real-infrastructure integration tests |
| Final unit suite after adding retry tests | 19 discovered, 13 passed, 6 opt-in integration tests skipped | Adds 2 bounded retry/idempotency tests; no claim of rerunning integration |
| Postman collection against packaged application | 8 requests, 15 assertions, 0 failures | Health, queue/poll, four expected records, history, documents, invalid input and missing job |
| Frontend production build | Passed | React JavaScript compiled by Vite |
| Docker application build and launch | Passed | React assets packaged with non-root Java runtime; seven project containers running |
| Mermaid validation | 10 of 10 sources parsed successfully | Syntax only, not a claim of screenshot review of every rendered diagram |
| Redis answer caching | First query uncached, identical follow-up cached | Four actual database records retained |
| Customer database stopped | PARTIAL, four order/product records, missing customer names and explicit warning | Fresh request bypassed old complete cache; partial result not cached |
| Customer database restarted | Health recovered before next failure scenario | Avoids confusing database recovery time with Redis behavior |
| Redis stopped | Fresh SQL query completed with four records | PostgreSQL job persistence and query execution remain usable |
| Missing OpenAI key | HTTP 503 with configuration instructions | No fake AI answer |
| Sample upload without key | WAITING_FOR_KEY | Original file persists; retry reports configuration requirement |
| Original source download | SHA-256 matched uploaded bytes | Real file-storage round trip |

The first combined outage-test attempt started its Redis check before the customer database had recovered, so it correctly observed a customer-shard PARTIAL result. The verification procedure was corrected to wait for shard recovery, then passed. No application result was relabelled to conceal this timing issue.

## Integration test details

The seven integration methods exercise real PostgreSQL association and bucket routing; actual database write rejection for the query-reader role; API validation and not-found responses; two provider 429 attempts and a bounded error; rejection of unknown citation D999; and real Kafka ingestion, duplicate detection, Qdrant retrieval and exact customer/product filtering. Test metadata is cleaned by fixture UUID.

Only the Gemini HTTP provider is simulated in that suite. Its fixture embeddings test filtering and plumbing, not semantic relevance. The simulation is local at port 4017 and is not configured in the normal application. Live provider checks are recorded separately above. See `postman/simulations/openai.context.yaml`.

## Browser checks

Firefox was used to observe the homepage, guided SQL execution progressing to COMPLETE, and the Records tab containing four expected customer/product matches. The document screen showed its empty state and then the uploaded return-policy document in WAITING_FOR_KEY. Saved query history showed complete and partial runs. API tests separately verified upload, retry and original-byte download.

A native file-picker automation limitation prevented completing an upload through the browser during the first attempt; the HTTP multipart upload was verified instead. Browser automation also exposed a stale connection-error message after an application rebuild. Document polling now clears its own recovered connection error without erasing upload errors, and query polling retries transient failures.

The final packaged UI was reloaded in Firefox. History navigation opened the saved result, Records showed the four expected rows with formatted INR prices, and the document retry button displayed the precise missing-key instructions. A 390 × 844 responsive viewport showed the compact layout; the icon-only submit control was given an explicit accessible label. This is a single responsive-layout check, not physical-device testing. Actual desktop screenshots are in [screenshots](screenshots/).

## Evidence and reproduction

Run the commands in [Testing](06-TESTING.md). Original build and execution logs are retained locally under ignored `.local/`; they are not shipped as application source. The source package includes tests, the Postman collection, the test-only provider simulation and [an actual guided query result](sample-query-result.json). The query IDs and timestamps in that result belong to this verification run and will differ on another computer.

## Not established

No live OpenAI calls were made. Gemini checks cover the sample questions above; no broad retrieval-quality evaluation, accuracy percentage or provider cost measurement is claimed. No production-scale benchmark, coverage percentage, public deployment, internet security certification, multi-user isolation or full mobile-browser test matrix is claimed. Windows and Linux setup paths are documented but were not executed on this macOS host. All stopped project services were restarted after the failure checks.
