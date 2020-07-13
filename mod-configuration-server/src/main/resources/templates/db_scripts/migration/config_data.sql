INSERT INTO ${myuniversity}_${mymodule}.config_data
SELECT md5(jsonb::text)::uuid, jsonb
FROM (
  SELECT jsonb - 'id'
    || jsonb_build_object('value', jsonb_build_object('name', json_array_elements(cast(jsonb->>'value' as json)->'selectedItems'))::text)
    || jsonb_build_object('configName', 'orders.prefix')
    || jsonb_build_object('code',  EXTRACT(EPOCH FROM now())::text || GENERATE_SERIES(1, json_array_length(cast(jsonb->>'value' as json)->'selectedItems')))
    AS jsonb
  FROM ${myuniversity}_${mymodule}.config_data
  WHERE jsonb->>'module' = 'ORDERS' AND jsonb->>'configName' = 'prefixes'
) x;

DELETE FROM ${myuniversity}_${mymodule}.config_data
WHERE jsonb->>'module' = 'ORDERS' AND jsonb->>'configName' = 'prefixes';

INSERT INTO ${myuniversity}_${mymodule}.config_data
SELECT md5(jsonb::text)::uuid, jsonb
FROM (
  SELECT jsonb - 'id'
    || jsonb_build_object('value', jsonb_build_object('name', json_array_elements(cast(jsonb->>'value' as json)->'selectedItems'))::text)
    || jsonb_build_object('configName', 'orders.suffix')
    || jsonb_build_object('code',  EXTRACT(EPOCH FROM now())::text || GENERATE_SERIES(1, json_array_length(cast(jsonb->>'value' as json)->'selectedItems')))
    AS jsonb
  FROM ${myuniversity}_${mymodule}.config_data
  WHERE jsonb->>'module' = 'ORDERS' AND jsonb->>'configName' = 'suffixes'
) x;

DELETE FROM ${myuniversity}_${mymodule}.config_data
WHERE jsonb->>'module' = 'ORDERS' AND jsonb->>'configName' = 'suffixes';
