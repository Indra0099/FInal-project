$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
docker compose up -d --wait product-db
if ($LASTEXITCODE -ne 0) { throw "Product database failed to start." }
Get-Content -Raw infra/enable-workspaces.sh | docker compose exec -T product-db bash -s
if ($LASTEXITCODE -ne 0) { throw "Workspace role setup failed." }
docker compose up --build -d
if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed. Check the messages above." }
Write-Host "Open http://localhost:8087 after the application starts."
