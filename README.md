# ShardQuery AI

**AI-powered queries across PostgreSQL databases and uploaded documents.** One React JavaScript frontend, one Java 21 / Spring Boot modular monolith, three PostgreSQL domain databases, Kafka, Redis and Qdrant.

Ask for database facts, search documents, or combine both. Answers expose actual records, document excerpts, citations and executed SQL. The interface uses a horizontal navigation bar and a two-column query workbench: Query workspace, My databases, Document library, History and How it works. Register or sign in to keep your own databases, documents and history separate.

## Run locally

**Setting up on another computer? Start with [INSTALL.md](INSTALL.md)** for prerequisites, download steps and Windows/macOS/Linux commands.

Install Docker Desktop (or Docker Engine with the Compose plugin). Start Docker, open a terminal in this folder, then:

```sh
# Creates .env only if missing and preserves existing settings.
sh scripts/start.sh
```

Windows PowerShell: run `powershell -ExecutionPolicy Bypass -File scripts/start.ps1`. Do not overwrite an existing `.env` containing your key.

Open **http://localhost:8087** and choose **Create an account** (no invitation token). The first build downloads images and Maven/npm dependencies. Check readiness with `docker compose ps` and `docker compose logs -f app`.

Without an API key, expand **Explore the sample database** and run a real cross-database SQL query. Natural-language planning, embeddings and AI answers require your personal Gemini API key from a project marked Free Tier with billing disabled. Uploaded files remain **Waiting for key** until configured and retried. There is no fake live-AI mode.

Read [the complete installation guide](docs/05-LOCAL-SETUP.md) for requirements, native development commands, Windows/macOS/Linux instructions, data persistence and troubleshooting.

## Demonstrate it

1. Sign in, then run the guided SQL explorer: **Purchases**, **Laptops**, **above ₹50,000**. Four delivered purchases match the fictional seed data.
2. Add your key to `.env`, then restart with `docker compose up -d --force-recreate app`.
3. Upload the supplied [sample documents](samples/README.md), using their listed product/customer IDs. Wait for **Ready**.
4. Ask: “Find customers who purchased laptops above ₹50,000 and summarize their uploaded product reviews.”
5. Inspect the records, cited excerpts and expandable execution details.

Only the sample business records and review documents are fictional. Database execution, Kafka processing, embeddings (when configured) and retrieval use the real services.

## Documentation

| Read | Purpose |
| --- | --- |
| [First-time installation](INSTALL.md) | Step-by-step setup on another computer |
| [Requirements](docs/00-REQUIREMENTS.md) | Project problem, scope and acceptance criteria |
| [Architecture](docs/01-ARCHITECTURE.md) | Component responsibilities and design decisions |
| [Database design](docs/02-DATABASE.md) | Tables, constraints, routing and association |
| [API guide](docs/03-API.md) | Request examples and response behavior |
| [OpenAPI specification](docs/openapi.json) | Machine-readable REST contract |
| [Build walkthrough](docs/04-BUILD-WALKTHROUGH.md) | Step-by-step explanation of the implementation |
| [Local setup](docs/05-LOCAL-SETUP.md) | Install and run on another computer |
| [Free Gemini setup](docs/09-FREE-GEMINI-SETUP.md) | Create your personal free-tier key and enable AI |
| [Testing](docs/06-TESTING.md) | Unit, integration, API and failure tests |
| [Demo and viva](docs/07-DEMO-AND-VIVA.md) | Presentation script and technical questions |
| [College project report](docs/08-PROJECT-REPORT.md) | Abstract, design, implementation, limits and references |
| [UML and workflows](docs/UML-AND-WORKFLOWS.md) | Component, class, ER, sequence, state and deployment diagrams |
| [Database management and connections](docs/13-DATABASE-MANAGEMENT-AND-CONNECTIONS.md) | Rename/delete databases, edit tables and connect external PostgreSQL data |
| [Personal query routing](docs/12-PERSONAL-QUERY-ROUTING.md) | Automatic schema selection and student/fee queries across your databases |
| [Accounts and personal databases](docs/11-ACCOUNTS-AND-DATABASES.md) | Register, create databases/tables, add/edit rows and query your data |
| [Frontend design](docs/10-FRONTEND-DESIGN.md) | Workbench layout, palette, screen structure and UI verification |
| [Verification results](docs/VERIFICATION.md) | Checks actually performed and remaining limitations |

## Source structure

```text
backend/                   Java 21 Spring Boot application and JUnit/Mockito tests
  src/main/java/com/shardquery/
    auth/                  Registration, BCrypt passwords, sessions and CSRF
    workspace/             Private PostgreSQL databases, tables and records
    ai/                    Gemini REST integration and structured output validation
    query/                 Query plans, SQL engine and durable orchestration
    document/              PDF/DOCX/TXT extraction and Qdrant/Kafka ingestion
    infrastructure/        Shard pools, Redis and Kafka configuration
    persistence/           JPA document and query-job metadata
    web/                   API controllers, validation and local request guards
frontend/                  React + JavaScript + Vite + Tailwind
infra/                     PostgreSQL initialization and fictional seed records
postman/                   API collection and test-only provider simulation
samples/                   Fictional documents for the live demonstration
docs/                      Technical design, setup, UML and college report
scripts/                   macOS/Linux and Windows start helpers
compose.yaml               Local deployment
```

## Accurate scope

The three databases are **domain shards**. Customer and order records additionally use three logical customer-ID hash buckets within their databases. Cross-shard association is performed in Java. This is a distributed-query demonstration, not a production distributed database cluster.

The SQL engine supports approved intents and typed filters, not arbitrary SQL. It uses parameter binding, read-only database credentials, execution deadlines and result caps. A combined result is not a globally consistent distributed snapshot. AI source citations and metadata filtering improve traceability but do not prove every generated sentence is correct.

This is a **local workspace with per-account access**. Published ports bind to loopback. Authentication, ownership checks and CSRF protection are implemented. Public hosting still requires HTTPS and operational hardening. See the [step-by-step personal database guide](docs/11-ACCOUNTS-AND-DATABASES.md) for capabilities, limits and upgrade behavior.
