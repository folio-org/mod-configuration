-- Database: myuniversity

-- only works if myuniversity doesnt have any objects depend on it
DROP ROLE IF EXISTS myuniversity;

CREATE USER myuniversity WITH CREATEDB PASSWORD 'myuniversity';

DROP DATABASE IF EXISTS myuniversity;

CREATE DATABASE myuniversity
  WITH OWNER = myuniversity
       ENCODING = 'WIN1252'
       TABLESPACE = pg_default
       LC_COLLATE = 'English_United States.1252'
       LC_CTYPE = 'English_United States.1252'
       CONNECTION LIMIT = -1;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO  myuniversity;

CREATE TABLE IF NOT EXISTS config_data (
   _id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   jsonb jsonb
   );
