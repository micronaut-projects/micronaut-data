package io.micronaut.data.runtime.config;

/**
 * Enum describing how to handle the schema at startup. Used for schema generation in
 * testing scenarios.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public enum SchemaGenerate {
    /**
     * Create the schema if it doesn't exist.
     */
    CREATE,
    /**
     * Drop and recreate the schema.
     */
    CREATE_DROP,
    /**
     * Do nothing.
     */
    NONE
}
