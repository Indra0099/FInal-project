# College demonstration and viva

## Before the presentation
Start the project, verify all services, run the guided SQL query and prepare the sample uploads. For AI, check the API key/credit and confirm one live request succeeds. Keep a key-free SQL demonstration ready if the provider is unavailable. Never describe the contract-test simulation as a live AI model.

## 8–10 minute demonstration
1. **Problem (1 minute):** Customer purchase facts live in databases; product reviews and policies live in documents. A joined answer needs both.
2. **Simple UI (1 minute):** Show Ask, Documents and History. Explain that database details are optional, not required for use.
3. **Structured execution (2 minutes):** Run Purchases → laptop → above ₹50,000. Show four delivered records. Expand the three SQL traces and customer-ID bucket example.
4. **Document ingestion (1 minute):** Upload a sample review with both IDs. Explain saved → queued → processing → ready. Show its excerpt count.
5. **Hybrid answer (2 minutes):** Ask the combined question, inspect citations and download a source. Explain how metadata stops a different customer's review from being mixed in.
6. **Reliability (1 minute):** Show partial-result behavior or explain the recorded failure check. Point out Redis TTL and durable job history.
7. **Design limits (1 minute):** Three domain databases; logical hash buckets; read-only approved SQL; no global snapshot or distributed writes.

## Expected seed result
Delivered laptop line items strictly above ₹50,000 belong to Asha (101/201), Ravi (102/202), Arjun (104/204) and Dev (106/202). Meera's ₹42,900 purchase is below threshold. Nila's order is cancelled and excluded. Asha's accessory purchase is not a laptop. These are fictional project fixtures, not market research.

## Viva questions with model answers
**Why three databases?** To demonstrate separate domain connections, routing and application-side result association. This is not a consensus-based database cluster.

**Is this horizontal sharding?** The three servers are domain partitions. Customer/order tables also have logical hash buckets. These are distinct ideas and are documented separately.

**How is a shard selected?** The intent selects required domains. A customer ID adds `customer_id % 3` as a local bucket predicate for customer/order queries.

**Does the AI execute SQL?** No. It proposes a constrained JSON plan. Java validates it, selects approved SQL templates and binds the values. Read-only database permissions are a second boundary.

**How is SQL injection prevented?** Parameter binding, no raw SQL endpoint, limited templates, positive/bounded typed parameters and SELECT-only database roles. A malicious category remains a string parameter.

**Why Kafka?** Document extraction and embedding are slower background jobs. Kafka decouples their processing from the upload request. Normal query submission does not travel through Kafka.

**What happens if publication fails after saving a file?** A durable QUEUED document row remains eligible for scheduled redispatch. The filename is UUID-based and metadata is saved before publishing.

**Is ingestion exactly once?** No. Delivery is at least once. A READY guard and deterministic Qdrant point IDs make repeated processing/upserts safe for the demonstrated case.

**Why Qdrant?** It stores embeddings and filters candidate excerpts by ready document, embedding model and product/customer metadata. It is not used for relational joins.

**What is an embedding?** A numerical vector representing text for similarity search. In this project the vector has 1536 dimensions. Similarity is not a confidence score about factual truth.

**How does hybrid retrieval avoid incorrect reviews?** SQL yields product/customer pairs. Qdrant must match those pairs, not merely either field independently. Empty SQL scope does not trigger unrestricted retrieval.

**What does Redis store?** Complete query results for 45 seconds, temporary job progress and health observations. PostgreSQL remains authoritative. An external sample-database update can leave a cached result stale until expiration. Personal-table queries bypass this cache.

**What happens on restart?** Files and metadata persist in volumes. Interrupted query jobs are marked failed with a rerun instruction. Interrupted indexing is requeued. Redis cache can be lost safely.

**What if a shard is down?** Customer/order failures produce explicit partial results where possible. The product node also hosts metadata, so losing it blocks durable workflows rather than providing full app availability.

**Can all generated claims be trusted?** No. The system constrains evidence and validates citation IDs, but does not independently prove each natural-language assertion. Users must inspect source excerpts.

**Why a modular monolith?** One student-maintainable service keeps deployment and debugging manageable while demonstrating real database, queue, cache and AI boundaries.

**What would production require?** Shared session/worker coordination, TLS, secrets management, ingestion isolation, measured scaling, backups/restores, distributed job coordination, stronger observability and cost controls.
