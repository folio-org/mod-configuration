-- Set the new schema first so that we dont have to namespace when creating tables
-- add the postgres to the search path so that we can use the pgcrypto extension
SET search_path TO myuniversity, public;

-- audit table to keep a history of the changes
-- made to a record. 
CREATE TABLE IF NOT EXISTS audit_config (
   _id UUID PRIMARY KEY,
   orig_id UUID NOT NULL,
   operation char(1) NOT NULL,
   jsonb jsonb,
   creation_date date not null 
   );

CREATE OR REPLACE FUNCTION audit_changes() RETURNS TRIGGER AS $config_audit$
    BEGIN
        IF (TG_OP = 'DELETE') THEN
            INSERT INTO audit_config SELECT gen_random_uuid(), OLD._id, 'D', OLD.jsonb, current_timestamp;
            RETURN OLD;
        ELSIF (TG_OP = 'UPDATE') THEN
            INSERT INTO audit_config SELECT gen_random_uuid(), NEW._id, 'U', NEW.jsonb, current_timestamp;
            RETURN NEW;
        ELSIF (TG_OP = 'INSERT') THEN
            INSERT INTO audit_config SELECT gen_random_uuid(), NEW._id, 'I', NEW.jsonb, current_timestamp;
            RETURN NEW;
        END IF;
        RETURN NULL; 
    END;
$config_audit$ LANGUAGE plpgsql;

CREATE TRIGGER audit AFTER INSERT OR UPDATE OR DELETE ON config_data FOR EACH ROW EXECUTE PROCEDURE audit_changes();

GRANT ALL PRIVILEGES ON audit_config TO myuniversity;
