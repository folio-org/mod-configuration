-- Database: testdb

DROP DATABASE IF EXISTS testdb;

CREATE DATABASE testdb
  WITH OWNER = username
       ENCODING = 'WIN1252'
       TABLESPACE = pg_default
       LC_COLLATE = 'English_United States.1252'
       LC_CTYPE = 'English_United States.1252'
       CONNECTION LIMIT = -1;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS config_data (
   _id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   jsonb jsonb
   );
