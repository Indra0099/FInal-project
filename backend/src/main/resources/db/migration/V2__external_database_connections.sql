ALTER TABLE app.user_databases ADD COLUMN kind varchar(16) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE app.user_databases ADD COLUMN connection_secret text;
ALTER TABLE app.user_databases ADD COLUMN remote_schema varchar(63);
ALTER TABLE app.user_databases ADD COLUMN connection_info text;
ALTER TABLE app.user_tables ADD COLUMN remote_schema varchar(63);
ALTER TABLE app.user_databases ADD CONSTRAINT database_kind CHECK (kind IN ('LOCAL','EXTERNAL'));
ALTER TABLE app.user_tables ALTER COLUMN name TYPE varchar(63);
