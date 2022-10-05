package io.micronaut.data.cosmos.config;

/**
 * This enum is used to tell how we should handle database and containers during startup.
 *
 * @author radovanradic
 * @since 4.0.0
 */
public enum StorageUpdatePolicy {

    /**
     * Database (or container) will be created if not exists with given properties from the configuration.
     */
    CREATE_IF_NOT_EXISTS,

    /**
     * Database (or container) will be updated with the properties from the configuration.
     */
    UPDATE,

    /**
     * No attempt to create or update database (or container) will be made.
     */
    NONE
}
