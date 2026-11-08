CREATE TABLE app.users (
 id uuid PRIMARY KEY, name varchar(80) NOT NULL, email varchar(254) NOT NULL UNIQUE,
 password_hash varchar(100) NOT NULL, created_at timestamptz NOT NULL,
 CONSTRAINT normalized_email CHECK (email = lower(email))
);
ALTER TABLE app.documents ADD COLUMN user_id uuid REFERENCES app.users(id);
ALTER TABLE app.query_jobs ADD COLUMN user_id uuid REFERENCES app.users(id);
CREATE INDEX documents_owner_status_idx ON app.documents(user_id,status);
CREATE INDEX query_jobs_owner_created_idx ON app.query_jobs(user_id,created_at DESC);
CREATE TABLE app.user_databases (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app.users(id),
 name varchar(60) NOT NULL, physical_name varchar(63) NOT NULL UNIQUE,
 status varchar(20) NOT NULL, created_at timestamptz NOT NULL,
 UNIQUE(user_id,name)
);
CREATE TABLE app.user_tables (
 id uuid PRIMARY KEY, database_id uuid NOT NULL REFERENCES app.user_databases(id),
 name varchar(60) NOT NULL, physical_name varchar(63) NOT NULL,
 columns_json text NOT NULL, created_at timestamptz NOT NULL,
 UNIQUE(database_id,name)
);
