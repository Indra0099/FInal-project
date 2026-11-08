# Install and run on another computer

For a guided first-time installation from GitHub, start with [INSTALL.md](../INSTALL.md). This page also covers native development and advanced troubleshooting.

## Route A — simplest: Docker only

### 1. Install prerequisites
- Windows 10/11: Docker Desktop with WSL 2 enabled and virtualization available. Start Docker Desktop and wait until its engine is running.
- macOS: Docker Desktop for your Apple Silicon or Intel processor. Colima is an alternative for experienced users.
- Linux: Docker Engine plus the Docker Compose v2 plugin. Follow your distribution's Docker installation instructions.
- A modern browser. Git is optional if you obtain a source ZIP instead of cloning a repository.
- Recommended: 16 GB system RAM, four CPU cores, 6 GB available to Docker and at least 8 GB free disk. Initial image/dependency downloads need internet access.

You do not need to install Java, Node, PostgreSQL, Kafka, Redis or Qdrant separately for this route. The images supply them.

### 2. Obtain the project
Clone the repository:

```sh
git clone https://github.com/Indra0099/FInal-project.git
cd FInal-project
```

Alternatively, download **Code → Download ZIP** from the repository and extract it. Open a terminal in the extracted folder containing `compose.yaml`. Keep paths with spaces quoted when using `cd`.

### 3. Create the environment file once

macOS/Linux:
```sh
cp .env.example .env
```
Windows PowerShell:
```powershell
Copy-Item .env.example .env
```

Skip this step if `.env` already exists. It contains local demonstration passwords; change them before the first startup if desired. These variables initialize roles on fresh database volumes. Editing passwords later does not automatically change already-created database users.

For live AI, set `GEMINI_API_KEY` to your own key. Keep the value on the server, never in `frontend/` or a variable prefixed `VITE_`. Use a personal Google AI Studio project marked Free Tier without enabling billing. Free-tier quotas and eligibility apply; the app cannot inspect billing status. No OpenAI or paid-provider fallback is configured. See [Free Gemini setup](09-FREE-GEMINI-SETUP.md). This project does not require a key to run the guided SQL explorer.

### 4. Build and start
```sh
sh scripts/start.sh
```

For Windows PowerShell, use `powershell -ExecutionPolicy Bypass -File scripts/start.ps1`. The helpers also upgrade existing installations with the workspace database role.

This builds React, packages Spring Boot and starts the app plus three PostgreSQL instances, Kafka, Redis and Qdrant. Initial startup can take several minutes. A rebuild uses Docker caches.

```sh
docker compose ps
docker compose logs --tail 80 app
```

### 5. Open the application
Visit **http://localhost:8087**. Choose **Create an account**, enter your name/email/password and register. No invitation token is needed. Use **Explore the sample database** for a key-free demonstration.

Open **My databases** to create your own database, define a table and add records. Follow [the illustrated workflow and full steps](11-ACCOUNTS-AND-DATABASES.md). Existing anonymous history/documents are preserved but not assigned to new accounts.

### 6. Enable AI and upload documents
After editing the key, run:
```sh
docker compose up -d --force-recreate app
```

Open Documents, upload a sample and wait for READY. For an existing WAITING_FOR_KEY file, choose Retry indexing. Follow `samples/README.md` for product/customer associations. Then run the examples in the question screen.

### 7. Stop and resume
```sh
docker compose stop
# Later:
docker compose start
```

Or remove containers while preserving their named volumes:
```sh
docker compose down
# Recreate them later:
docker compose up -d
```

**Do not add `-v` unless you deliberately intend to delete all project databases, uploaded files, Kafka data and vectors.** Redis is disposable; other volumes contain history/data.

## Route B — native Java/React development

Install Java 21 JDK, Maven 3.9+, Node.js 22.12+ with npm, and Docker for infrastructure. Java 21 must be selected by your terminal's JAVA_HOME/PATH. Verify with `java -version`, `mvn -version`, `node --version`, `docker compose version`.

1. Create `.env` as above.
2. Start only infrastructure:
```sh
docker compose up -d customer-db order-db product-db kafka redis qdrant
docker compose up -d --wait product-db
docker compose exec -T product-db bash -s < infra/enable-workspaces.sh
```
3. In a backend terminal, export the variables you configured in `.env` (APP_PASSWORD, READER_PASSWORD, WORKSPACE_PASSWORD and optionally GEMINI_API_KEY). With untouched local defaults, passwords already match `application.yml`. Use your shell's environment syntax; do not print secrets into terminal logs.
```sh
cd backend
mvn spring-boot:run
```
4. In another terminal:
```sh
cd frontend
npm ci
npm run dev
```
5. Open **http://localhost:5178**. Vite proxies `/api` to port 8087. The backend does not automatically load the root `.env` when run natively; Docker Compose does. For native live AI, explicitly set the environment in the backend terminal.

Do not run the Docker app and native backend simultaneously on port 8087. Use `docker compose stop app` before native development. Native document files go to `backend/uploads`; Docker files live in its named upload volume.

## Colima on this development computer
This project was tested in an isolated `shardquery` profile. Its commands explicitly select the context so existing projects are unaffected:
```sh
colima start shardquery --cpu 4 --memory 6 --disk 30 --activate=false
docker --context colima-shardquery compose up --build -d
```
On a normal Docker Desktop system, omit `--context colima-shardquery`.

## Ports
| Service | Local port |
| --- | --- |
| Application | 8087 (APP_PORT can change this) |
| Vite development | 5178 |
| Customer / order / product PostgreSQL | 5641 / 5642 / 5643 |
| Redis | 6391 |
| Kafka external listener | 9192 |
| Qdrant HTTP | 6343 |

All published Compose ports bind to 127.0.0.1. Another computer cannot access them remotely by default; copy the project and run it locally there.

## Troubleshooting
| Symptom | Check |
| --- | --- |
| Cannot connect to Docker | Start Docker Desktop/Engine or the intended Colima profile. |
| Port already allocated | Stop the other local service or change the corresponding Compose port and native configuration. |
| Database authentication failed after changing .env | Existing roles retain their initial passwords. Restore the matching values or deliberately migrate the roles. |
| Kafka exits with AccessDeniedException | The log volume must be writable by UID 1000. This project mounts the image's prepared `/var/lib/kafka/data` path. Do not substitute an unowned directory. |
| Upload remains queued | Check `docker compose logs kafka app` and the AI provider configuration. Pending work is retained for redispatch. |
| Document waiting for key | Configure the key, recreate the app, then Retry indexing. |
| Provider quota error | Check API billing, quota and the configured model's account availability. |
| Empty document | Scanned/image-only PDFs need OCR outside this project. |
| No hybrid sources | Check READY state and exact product/customer metadata; unrelated documents are intentionally excluded. |
| Sources look stale | Indexing is not live file synchronization; upload the updated file. SQL cache lives for at most 45 seconds. |
| Browser has old assets after rebuild | Reload the page after the new app starts. |
| Product database down | It also hosts metadata; workflows are unavailable until it recovers. |

Before showing real personal or confidential documents, obtain appropriate permission to send their text to the configured AI provider. This demo is supplied with fictional records.

## Manage or connect databases

Open My databases for rename, delete, row and column controls. Follow [the connection guide](13-DATABASE-MANAGEMENT-AND-CONNECTIONS.md) to enable a PostgreSQL hostname, create a SELECT-only login and connect its tables. Preserve the upload volume: it contains the encryption key required to reopen saved connections.
