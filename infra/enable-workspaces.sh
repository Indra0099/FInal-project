#!/usr/bin/env bash
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v workspace_password="${WORKSPACE_PASSWORD:-local-workspaces-change-me}" <<'SQL'
SELECT 'CREATE ROLE shard_workspace LOGIN CREATEDB' WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='shard_workspace') \gexec
ALTER ROLE shard_workspace WITH LOGIN CREATEDB NOSUPERUSER NOCREATEROLE PASSWORD :'workspace_password';
ALTER ROLE shard_workspace SET statement_timeout = '8s';
ALTER ROLE shard_workspace SET idle_in_transaction_session_timeout = '10s';
SQL
