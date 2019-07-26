package io.micronaut.data.model.query.builder.sql;

/**
 * The SQL dialect to use.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public enum Dialect {
    /**
     * H2 database.
     */
    H2,
    /**
     * MySQL 5.5 or above.
     */
    MYSQL(false),
    /**
     * Postgres 9.5 or later.
     */
    POSTGRES,
    /**
     * SQL server 2012 or above.
     */
    SQL_SERVER(false),
    /**
     * Oracle 12c or above.
     */
    ORACLE,
    /**
     * Ansi compliant SQL.
     */
    ANSI;

    private final boolean supportsBatch;

    /**
     * Default constructor.
     */
    Dialect() {
        this(true);
    }

    /**
     * Allows customization of batch support.
     * @param supportsBatch If batch is supported
     */
    Dialect(boolean supportsBatch) {
        this.supportsBatch = supportsBatch;
    }

    /**
     * Some drivers and dialects do not support JDBC batching. This allows customization.
     * @return True if batch is supported.
     */
    public boolean allowBatch() {
        return supportsBatch;
    }
}
