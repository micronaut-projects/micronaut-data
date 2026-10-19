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
    key_value jsonb;
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
    FOR i IN 1..TG_NARGS - 1 LOOP
        key_name := TG_ARGV[i];
        IF key_name = '' OR key_name = ANY(key_names) THEN
            RAISE EXCEPTION 'Primary-key column names must be non-empty and distinct';
        END IF;
        key_names := array_append(key_names, key_name);
        -- Convert only the selected key: unrelated columns may contain values that jsonb cannot represent.
        -- Quote the column identifier and bind the record separately from the SQL text.
        IF TG_OP IN ('UPDATE', 'DELETE') THEN
            BEGIN
                EXECUTE format('SELECT to_jsonb(($1).%I)', key_name) INTO key_value USING OLD;
            EXCEPTION WHEN undefined_column THEN
                RAISE EXCEPTION 'Old primary-key column % must exist and contain a non-null JSON scalar', key_name;
            END;
            IF key_value IS NULL OR jsonb_typeof(key_value) NOT IN ('string', 'number', 'boolean') THEN
                RAISE EXCEPTION 'Old primary-key column % must exist and contain a non-null JSON scalar', key_name;
            END IF;
            old_keys := old_keys || jsonb_build_object(key_name, key_value #>> '{}');
        END IF;
        IF TG_OP IN ('INSERT', 'UPDATE') THEN
            BEGIN
                EXECUTE format('SELECT to_jsonb(($1).%I)', key_name) INTO key_value USING NEW;
            EXCEPTION WHEN undefined_column THEN
                RAISE EXCEPTION 'New primary-key column % must exist and contain a non-null JSON scalar', key_name;
            END;
            IF key_value IS NULL OR jsonb_typeof(key_value) NOT IN ('string', 'number', 'boolean') THEN
                RAISE EXCEPTION 'New primary-key column % must exist and contain a non-null JSON scalar', key_name;
            END IF;
            new_keys := new_keys || jsonb_build_object(key_name, key_value #>> '{}');
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
