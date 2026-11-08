# Requirements and acceptance criteria

## Problem
Useful information is split between operational database records and documents. A student or analyst should be able to ask a question, see which data was used, and inspect the evidence without writing SQL or understanding the infrastructure.

## Implemented scope
- Natural-language planning through Gemini with strict JSON output.
- SQL, RAG and hybrid query workflows.
- Guided deterministic SQL exploration without an AI key.
- Five SQL intents: customers, products, delivered purchases, structured reviews and inventory.
- Typed category, minimum price, customer ID and product ID filters where meaningful.
- Three independent PostgreSQL databases with separate read-only pools.
- Customer-ID modulo hash buckets in customer/order tables and bounded Java-side associations.
- PDF, DOCX and UTF-8 TXT uploads, extraction, overlapping chunks and embedding generation.
- Kafka ingestion with a durable pending row, bounded processing attempts and explicit retry.
- Qdrant search with ready-document and model filters; hybrid filters exact product/customer associations.
- Redis query caching (45 seconds), job progress and health observations (5 seconds).
- Persistent query history, status polling, record tables, source excerpts and Markdown answer export.

## Non-functional requirements
Bound input length, uploads, text extraction, chunks, pool sizes, concurrent jobs and result size. Queries use statement/connection/provider deadlines. A missing dependency must produce a visible error or partial status. No raw model-produced SQL is executed. API keys stay server-side and out of Git. Retrieved text is evidence only. The UI must remain usable on desktop and mobile.

## Acceptance examples
1. Guided purchases with category laptop and minimum unit price 50000 return four delivered rows, associated with Asha, Ravi, Arjun and Dev.
2. Customer 101 routes to bucket 2 and returns only Asha's matching laptop purchase.
3. A query-reader connection cannot write to a business table.
4. Duplicate file bytes with the same association metadata and embedding model return the existing document rather than adding another.
5. A review linked to customer 999 must not appear in the hybrid results for customer 101/product 201.
6. Unavailable shards and truncated results are labelled, and these partial responses are not cached.
7. Without an AI key, AI requests explain the missing configuration and the SQL explorer remains available.
8. Actual tests, simulated-provider tests and live-provider checks are reported separately.

## Deliberate exclusions
Arbitrary SQL, SQL joins across remote servers, distributed writes, consensus, multi-region replication, OCR, internet hosting, production backups, aggregate queries over personal tables, editable sample commerce databases and production performance claims. These are future extensions, not hidden completed features.

## Account and personal-data extension
Registration, sign-in/sign-out, owner-scoped documents/history, real PostgreSQL database/table creation, typed row insertion/editing, pagination and bounded natural-language filtering/sorting are implemented. See [scope and limits](11-ACCOUNTS-AND-DATABASES.md).
