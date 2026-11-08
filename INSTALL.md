# ShardQuery AI — install on another computer

This guide runs the complete project locally: the React frontend, Spring Boot backend, three PostgreSQL databases, Kafka, Redis and Qdrant. Use Docker for the simplest setup.

**Repository:** https://github.com/Indra0099/FInal-project

## 1. Check the computer requirements

- A Windows, macOS or Linux version supported by the Docker installer linked below.
- Recommended for this project: 16 GB RAM, four CPU cores and at least 8 GB free disk space. Allow Docker about 6 GB RAM where resource allocation is configurable.
- Internet for the first build and for Gemini AI requests.
- A modern browser such as Firefox, Chrome or Edge.

These are project recommendations, not a claim that every computer has been tested. See [recorded verification](docs/VERIFICATION.md) for the environments and checks actually run.

## 2. Install Docker and optionally Git

| Computer | Installation |
| --- | --- |
| Windows | Install [Docker Desktop for Windows](https://docs.docker.com/desktop/setup/install/windows-install/). Use its Linux-container backend; follow the installer instructions for WSL 2 and virtualization. Restart Windows if prompted, then open Docker Desktop. |
| macOS | Install [Docker Desktop for Mac](https://docs.docker.com/desktop/setup/install/mac-install/). Choose the download for Apple Silicon or Intel, move Docker to Applications, then open it. |
| Linux | Install [Docker Engine](https://docs.docker.com/engine/install/) for your distribution and the [Docker Compose plugin](https://docs.docker.com/compose/install/linux/). Start Docker and complete the official instructions for running Docker as your user. |

To clone the repository, install [Git](https://git-scm.com/downloads). Git is optional if you download the project ZIP in step 3. A code editor such as VS Code is optional.

**You do not need separate Java, Maven, Node.js, PostgreSQL, Redis, Kafka or Qdrant installations for the Docker setup.** Docker builds and runs them.

Open PowerShell on Windows or Terminal on macOS/Linux. Check:

```sh
docker --version
docker compose version
docker info
```

`docker info` must connect to the Docker engine. If it cannot, start Docker Desktop/Engine before continuing. Use `docker compose` with a space, as shown in this guide.

## 3. Download the project

### Option A: Git

```sh
git clone https://github.com/Indra0099/FInal-project.git
cd FInal-project
```

### Option B: ZIP, without Git

1. Open [the repository](https://github.com/Indra0099/FInal-project).
2. Select **Code → Download ZIP**.
3. Extract the ZIP completely; do not run commands inside the compressed archive.
4. Open a terminal inside the extracted folder, usually `FInal-project-main`.

The folder must contain `compose.yaml`, `Dockerfile`, `backend`, `frontend`, `infra` and `scripts`. Run all remaining Docker commands from this folder.

## 4. Create your local configuration

The repository includes `.env.example`. Your real `.env` is intentionally excluded from GitHub.

**Windows PowerShell:**

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

**macOS/Linux:**

```sh
test -f .env || cp .env.example .env
nano .env
```

You can use another text editor instead. In nano, save with Ctrl+O, press Enter, then exit with Ctrl+X. Keep the filename exactly `.env`, not `.env.txt`.

For a local demonstration, the supplied database passwords work together. If you choose different passwords, set them **before the first startup**. Changing `.env` later does not change passwords already stored inside existing database volumes.

For AI features, fill in your own key:

```dotenv
GEMINI_API_KEY=your-own-key-here
```

Do not use that placeholder literally. Follow [the Gemini setup guide](docs/09-FREE-GEMINI-SETUP.md) to obtain a key from your own eligible free-tier project. Quotas and model availability depend on your Google account; the application cannot verify billing status. Never upload `.env` or put the key in frontend code.

You may leave the key empty to try the guided sample SQL explorer and manage local database records. Natural-language questions, document embeddings and RAG require a working Gemini key.

## 5. Build and start the whole project

Make sure Docker is running.

**Windows PowerShell:**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start.ps1
```

This applies the execution-policy option only to the helper's PowerShell process.

**macOS/Linux:**

```sh
sh scripts/start.sh
```

The helper creates `.env` if missing, starts the product database, ensures the workspace database role exists, then builds and starts every service. The first run downloads dependencies and can take several minutes.

Check progress:

```sh
docker compose ps
docker compose logs --tail 80 app
```

The application container should be running, with a log indicating Spring Boot has started on port 8087. The PostgreSQL, Redis and Kafka health checks should become healthy. Qdrant and the app do not have Compose health checks, so their status says running rather than healthy.

## 6. Open and sign in

1. Open **http://localhost:8087** on this computer.
2. Select **Create an account**.
3. Enter your name, email and a password, then register. No invitation token is required.
4. Use that account to sign in on this installation.

A fresh installation starts with fictional sample commerce records. Accounts, personal databases, uploads and history from another computer are **not copied by Git clone**. Create a new account here. Moving existing data requires a separate backup and restore of the database and upload volumes.

## 7. Check the main features

### A. Run a query without an AI key

1. Open **Query workspace**.
2. Expand **Explore the sample database**.
3. Select purchases, laptops and a minimum price of 50,000.
4. Run the query and inspect the records. This executes against the seeded PostgreSQL databases.

### B. Create your own database and table

1. Open **My databases** and create `College records`.
2. Add a table named `Students`.
3. Add columns such as `student_name` (TEXT) and `marks` (INTEGER).
4. Save the table and use **Add row** to enter fictional practice records.
5. With a Gemini key configured, choose **Ask about this table** and ask: `Show students with marks above 80, sorted by marks from highest to lowest.`
6. Use the table/database management controls to rename or delete practice data. The deletion dialog names the selected item and requires **Delete permanently**; Cancel keeps it unchanged.

See [accounts and tables](docs/11-ACCOUNTS-AND-DATABASES.md) and [database management](docs/13-DATABASE-MANAGEMENT-AND-CONNECTIONS.md) for full steps and limits.

### C. Try document and combined answers

1. Configure Gemini in `.env`.
2. Apply configuration changes:

   ```sh
   docker compose up -d --force-recreate app
   ```

3. Refresh the browser and sign in again if the restart expired your session.
4. Open **Document library** and upload the fictional documents in `samples/`.
5. Follow [samples/README.md](samples/README.md) for the correct customer/product associations.
6. Wait until documents show **Ready**. For a previous **Waiting for key** upload, select **Retry indexing**.
7. Ask a document question or the combined sample question from [the demo guide](docs/07-DEMO-AND-VIVA.md).

Connected PostgreSQL databases are optional. Follow [the external connection guide](docs/13-DATABASE-MANAGEMENT-AND-CONNECTIONS.md) for host allowlisting, a SELECT-only account and connection URLs. Connected remote tables remain read-only.

## 8. Stop and run again later

Stop services while retaining your data:

```sh
docker compose stop
```

After restarting Docker, return to the project folder and run:

```sh
docker compose start
```

If containers were removed with `docker compose down`, recreate them with `docker compose up -d`. Named volumes retain data. Do not add `-v` to the down command unless you intend to erase the stored project data.

## 9. Install future code updates

For a clean Git checkout:

```sh
git pull --ff-only
```

Then rerun the start helper from step 5 to rebuild. Refresh the browser when startup finishes. The helper preserves an existing `.env`. If Git reports local changes or a conflict, resolve those deliberately instead of resetting or overwriting your work.

Preserve the database and upload volumes when updating. The upload volume also holds the encryption key used for saved external database connections.

## 10. Troubleshooting

| Problem | What to do |
| --- | --- |
| `docker` is not recognized | Install Docker, reopen the terminal, and check `docker --version`. |
| Cannot connect to the Docker daemon | Start Docker Desktop/Engine and verify `docker info`. |
| `no configuration file provided` | Change into the folder containing `compose.yaml`. |
| First build fails to download packages or images | Check internet/proxy connectivity, read the failed step, then rerun the helper. |
| Build stops or a container exits with code 137 | Check Docker/system memory and free disk space; increase available resources and retry. |
| Port 8087 is already in use | Set `APP_PORT=8088` in `.env`, run `docker compose up -d --force-recreate app`, and open http://localhost:8088. Other service ports are listed in the extended setup guide. |
| Database password error after editing `.env` | Existing volumes retain their initialized role passwords. Restore the matching settings or perform a deliberate database password migration. |
| Website does not open | Check `docker compose ps` and `docker compose logs --tail 80 app`. Wait until the application has started. |
| Missing Gemini key or quota error | Check your own key and provider quota, then recreate the app. The guided SQL explorer remains available without AI. |
| Delete button appears disabled or old UI remains | Refresh after the rebuilt app starts, sign in again, and reopen the selected item's dialog. |
| Request could not be verified | Refresh the page and sign in again; server restarts can expire the session/CSRF token. |

## Further documentation

- [Extended setup and native Java/React development](docs/05-LOCAL-SETUP.md)
- [Testing commands and integration requirements](docs/06-TESTING.md)
- [Step-by-step build walkthrough](docs/04-BUILD-WALKTHROUGH.md)
- [UML and workflow diagrams](docs/UML-AND-WORKFLOWS.md)
- [College project report](docs/08-PROJECT-REPORT.md)

The localhost address works on the computer running Docker. This guide creates a local installation; it does not publish the application on the internet.
