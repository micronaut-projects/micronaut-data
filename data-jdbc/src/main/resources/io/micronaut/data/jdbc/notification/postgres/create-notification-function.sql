-- Copyright 2017-2026 original authors.
-- Licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
-- Install through a database migration in the schema that owns the notification function.
-- Arguments: notification channel, followed by exact mapped primary-key column names.
CREATE OR REPLACE FUNCTION micronaut_data_notify_change() RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $micronaut$
DECLARE
    channel_name text;
    key_name text;
    key_names text[] := ARRAY[]::text[];
    old_row jsonb;
    new_row jsonb;
    old_keys jsonb := '{}'::jsonb;
    new_keys jsonb := '{}'::jsonb;
    operation_name text := TG_OP;
    payload jsonb;
BEGIN
    IF TG_NARGS < 2 THEN
        RAISE EXCEPTION 'micronaut_data_notify_change requires a channel and primary-key column names';
    END IF;
    channel_name := TG_ARGV[0];
    IF channel_name !~ '[^[:space:]]' OR octet_length(convert_to(channel_name, 'UTF8')) > 63 THEN
        RAISE EXCEPTION 'Notification channel must be non-blank and at most 63 UTF-8 bytes';
    END IF;
    IF TG_WHEN <> 'AFTER' OR NOT (
        (TG_LEVEL = 'ROW' AND TG_OP IN ('INSERT', 'UPDATE', 'DELETE')) OR
        (TG_LEVEL = 'STATEMENT' AND TG_OP = 'TRUNCATE')
    ) THEN
        RAISE EXCEPTION 'Use an AFTER ROW INSERT/UPDATE/DELETE or AFTER STATEMENT TRUNCATE trigger';
    END IF;
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        old_row := to_jsonb(OLD);
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        new_row := to_jsonb(NEW);
    END IF;
    FOR i IN 1..TG_NARGS - 1 LOOP
        key_name := TG_ARGV[i];
        IF key_name = '' OR key_name = ANY(key_names) THEN
            RAISE EXCEPTION 'Primary-key column names must be non-empty and distinct';
        END IF;
        key_names := array_append(key_names, key_name);
        IF old_row IS NOT NULL THEN
            IF NOT (old_row ? key_name) OR jsonb_typeof(old_row -> key_name) NOT IN ('string', 'number', 'boolean') THEN
                RAISE EXCEPTION 'Old primary-key column % must exist and contain a non-null JSON scalar', key_name;
            END IF;
            old_keys := old_keys || jsonb_build_object(key_name, old_row ->> key_name);
        END IF;
        IF new_row IS NOT NULL THEN
            IF NOT (new_row ? key_name) OR jsonb_typeof(new_row -> key_name) NOT IN ('string', 'number', 'boolean') THEN
                RAISE EXCEPTION 'New primary-key column % must exist and contain a non-null JSON scalar', key_name;
            END IF;
            new_keys := new_keys || jsonb_build_object(key_name, new_row ->> key_name);
        END IF;
    END LOOP;
    IF TG_OP = 'TRUNCATE' THEN
        operation_name := 'INVALIDATE';
    END IF;
    payload := jsonb_build_object(
        'version', 1, 'schema', TG_TABLE_SCHEMA, 'table', TG_TABLE_NAME,
        'operation', operation_name, 'oldKeys', old_keys, 'newKeys', new_keys
    );
    -- NOTIFY rejects payloads of 8000 bytes or more. Preserve the table identity on overflow.
    IF octet_length(payload::text) >= 8000 THEN
        payload := jsonb_build_object(
            'version', 1, 'schema', TG_TABLE_SCHEMA, 'table', TG_TABLE_NAME,
            'operation', 'INVALIDATE', 'oldKeys', '{}'::jsonb, 'newKeys', '{}'::jsonb
        );
    END IF;
    PERFORM pg_notify(channel_name, payload::text);
    RETURN NULL;
END;
$micronaut$;
