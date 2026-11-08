#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then cp .env.example .env; fi
docker compose up -d --wait product-db
docker compose exec -T product-db bash -s < infra/enable-workspaces.sh
docker compose up --build -d
printf '\nOpen http://localhost:8087 after the application starts.\n'
