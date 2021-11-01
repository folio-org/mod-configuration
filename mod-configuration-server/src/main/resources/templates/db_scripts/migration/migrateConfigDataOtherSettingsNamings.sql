UPDATE ${myuniversity}_${mymodule}.config_data SET jsonb = regexp_replace(jsonb::text, 'BARCODE', 'barcode')::jsonb
WHERE jsonb ->> 'module' = 'CHECKOUT' AND jsonb ->> 'configName' = 'other_settings';

UPDATE ${myuniversity}_${mymodule}.config_data SET jsonb = regexp_replace(jsonb::text, 'EXTERNAL', 'externalSystemId')::jsonb
WHERE jsonb ->> 'module' = 'CHECKOUT' AND jsonb ->> 'configName' = 'other_settings';

UPDATE ${myuniversity}_${mymodule}.config_data SET jsonb = regexp_replace(jsonb::text, 'FOLIO', 'id')::jsonb
WHERE jsonb ->> 'module' = 'CHECKOUT' AND jsonb ->> 'configName' = 'other_settings';

UPDATE ${myuniversity}_${mymodule}.config_data SET jsonb = regexp_replace(jsonb::text, 'USER', 'username')::jsonb
WHERE jsonb ->> 'module' = 'CHECKOUT' AND jsonb ->> 'configName' = 'other_settings';