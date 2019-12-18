CREATE EXTENSION "uuid-ossp";

INSERT INTO ${myuniversity}_${mymodule}.config_data
SELECT uuid_generate_v4(),
  jsonb - 'id' || jsonb_build_object('value', jsonb_build_object('name', json_array_elements(cast(jsonb->>'value' as json)->'selectedItems'))::text)
  || jsonb_build_object('configName', 'orders.prefix')
  || jsonb_build_object('code',  EXTRACT(EPOCH FROM now())::text || GENERATE_SERIES(1, json_array_length(cast(jsonb->>'value' as json)->'selectedItems')))
FROM ${myuniversity}_${mymodule}.config_data
WHERE jsonb->>'module' = 'ORDERS' AND jsonb->>'configName' = 'prefixes';

DELETE FROM ${myuniversity}_${mymodule}.config_data
WHERE jsonb->>'module' = 'ORDERS' AND jsonb->>'configName' = 'prefixes';

INSERT INTO ${myuniversity}_${mymodule}.config_data
SELECT uuid_generate_v4(),
  jsonb - 'id' || jsonb_build_object('value', jsonb_build_object('name', json_array_elements(cast(jsonb->>'value' as json)->'selectedItems'))::text)
  || jsonb_build_object('configName', 'orders.suffix')
  || jsonb_build_object('code',  EXTRACT(EPOCH FROM now())::text || GENERATE_SERIES(1, json_array_length(cast(jsonb->>'value' as json)->'selectedItems')))
FROM ${myuniversity}_${mymodule}.config_data
WHERE jsonb->>'module' = 'ORDERS' AND jsonb->>'configName' = 'suffixes';

DELETE FROM ${myuniversity}_${mymodule}.config_data
WHERE jsonb->>'module' = 'ORDERS' AND jsonb->>'configName' = 'suffixes';

DROP EXTENSION "uuid-ossp";
