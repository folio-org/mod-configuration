-- remove access to public schema to all
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- create tenant user in db
-- DROP USER IF EXISTS myuniversity;

CREATE USER myuniversity_mymodule WITH ENCRYPTED PASSWORD 'myuniversity';
ALTER USER myuniversity_mymodule WITH CONNECTION LIMIT 50;

-- remove this
-- GRANT ALL PRIVILEGES ON DATABASE postgres TO myuniversity;

-- create table space per tenant
-- CREATE TABLESPACE ts_myuniversity OWNER myuniversity LOCATION '${tablespace_dir}/myuniversity/module_to/module_from';
-- SET default_tablespace = ts_myuniversity;

-- DROP SCHEMA IF EXISTS myuniversity CASCADE;
-- The schema user wil be the schema name since not given
CREATE SCHEMA myuniversity_mymodule AUTHORIZATION myuniversity_mymodule;

-- for uuid generator -> gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Set the new schema first so that we dont have to namespace when creating tables
-- add the postgres to the search path so that we can use the pgcrypto extension
SET search_path TO myuniversity_mymodule, public;

CREATE TABLE IF NOT EXISTS config_data (
   _id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   jsonb jsonb,
   creation_date date not null default current_timestamp,
   update_date date not null default current_timestamp
   );

-- index to support @> ops, faster than jsonb_ops
CREATE INDEX idxgin_conf ON config_data USING gin (jsonb jsonb_path_ops);

-- update the update_date column when record is updated
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
-- NEW to indicate updating the new row value
    NEW.update_date = current_timestamp;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_date BEFORE UPDATE ON config_data FOR EACH ROW EXECUTE PROCEDURE  update_modified_column();

-- give the user PRIVILEGES after everything is created by script
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA myuniversity_mymodule TO myuniversity_mymodule;

COPY config_data (jsonb) FROM 'data/locales.data' ENCODING 'UTF8';
