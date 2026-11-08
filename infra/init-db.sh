#!/usr/bin/env bash
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v reader_password="$READER_PASSWORD" -v app_password="$APP_PASSWORD" <<'SQL'
CREATE ROLE query_reader LOGIN PASSWORD :'reader_password';
ALTER ROLE query_reader SET default_transaction_read_only = on;
ALTER ROLE query_reader SET statement_timeout = '4s';
ALTER ROLE query_reader SET idle_in_transaction_session_timeout = '5s';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /seed/schema.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
GRANT CONNECT ON DATABASE :DBNAME TO query_reader;
GRANT USAGE ON SCHEMA public TO query_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO query_reader;
SQL
if [ "$POSTGRES_DB" = "shard_product_db" ]; then
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v app_password="$APP_PASSWORD" <<'SQL'
CREATE ROLE shard_app LOGIN PASSWORD :'app_password';
CREATE SCHEMA app AUTHORIZATION shard_app;
SET ROLE shard_app;
CREATE TABLE app.documents (
 id uuid PRIMARY KEY, name varchar(255) NOT NULL, fingerprint varchar(64) NOT NULL UNIQUE,
 status varchar(30) NOT NULL, product_id bigint, customer_id bigint,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 attempts integer NOT NULL DEFAULT 0, chunks integer NOT NULL DEFAULT 0,
 error text, embedding_model varchar(100), next_dispatch timestamptz NOT NULL
);
CREATE INDEX documents_dispatch_idx ON app.documents(status, next_dispatch);
CREATE TABLE app.query_jobs (
 id uuid PRIMARY KEY, question text NOT NULL, mode varchar(20) NOT NULL,
 status varchar(30) NOT NULL, created_at timestamptz NOT NULL,
 completed_at timestamptz, result_json text, error text
);
CREATE INDEX query_jobs_created_idx ON app.query_jobs(created_at DESC);
SQL
fi
