REVOKE ALL PRIVILEGES ON DATABASE postgres from myuniversity_mymodule;
DROP SCHEMA IF EXISTS myuniversity_mymodule CASCADE;
-- DROP TABLESPACE IF EXISTS ts_myuniversity;
DROP USER IF EXISTS myuniversity_mymodule;
