CREATE EXTENSION "uuid-ossp";

CREATE OR REPLACE FUNCTION migrate_prefix_suffix(config_name TEXT) RETURNS void  AS $$
BEGIN
  INSERT INTO ${myuniversity}_${mymodule}.config_data
  SELECT uuid_generate_v4(),
    jsonb - 'id' || jsonb_build_object('value', jsonb_build_object('name', json_array_elements(cast(jsonb->>'value' as json)->'selectedItems'))::text)
    || jsonb_build_object('configName', 'orders.' || config_name)
    || jsonb_build_object('code',  EXTRACT(EPOCH FROM now())::text || GENERATE_SERIES(1, json_array_length(cast(jsonb->>'value' as json)->'selectedItems')))
  FROM ${myuniversity}_${mymodule}.config_data
  WHERE jsonb->>'module' = 'ORDERS' AND jsonb->>'configName' = config_name || 'es';

  DELETE FROM ${myuniversity}_${mymodule}.config_data
  WHERE jsonb->>'module' = 'ORDERS' AND jsonb->>'configName' = config_name || 'es';
END;
$$ LANGUAGE plpgsql

SELECT migrate_prefix_suffix('prefix');
SELECT migrate_prefix_suffix('suffix');

DROP FUNCTION migrate;
DROP EXTENSION IF EXISTS "uuid-ossp";
