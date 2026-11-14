-- Copyright 2017-2026 original authors.
-- Licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
-- Install through a database migration in the schema that owns the notification function.
-- Argument: notification channel. Sends one table invalidation per statement, including zero-row statements.
CREATE OR REPLACE FUNCTION micronaut_data_notify_invalidation() RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $micronaut$
DECLARE
    channel_name text;
BEGIN
    IF TG_NARGS <> 1 THEN
        RAISE EXCEPTION 'micronaut_data_notify_invalidation requires exactly one channel argument';
    END IF;
    channel_name := TG_ARGV[0];
    IF channel_name !~ '[^[:space:]]' OR octet_length(convert_to(channel_name, 'UTF8')) > 63 THEN
        RAISE EXCEPTION 'Notification channel must be non-blank and at most 63 UTF-8 bytes';
    END IF;
    IF TG_WHEN <> 'AFTER' OR TG_LEVEL <> 'STATEMENT' OR TG_OP NOT IN ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'Use an AFTER STATEMENT INSERT/UPDATE/DELETE/TRUNCATE trigger';
    END IF;
    PERFORM pg_notify(channel_name, jsonb_build_object(
        'version', 1, 'schema', TG_TABLE_SCHEMA, 'table', TG_TABLE_NAME,
        'operation', 'INVALIDATE', 'oldKeys', '{}'::jsonb, 'newKeys', '{}'::jsonb
    )::text);
    RETURN NULL;
END;
$micronaut$;
