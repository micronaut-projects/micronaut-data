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
    MYSQL,
    /**
     * Postgres 9.5 or later.
     */
    POSTGRES,
    /**
     * SQL server 2012 or above.
     */
    SQL_SERVER,
    /**
     * Oracle 12c or above.
     */
    ORACLE,
    /**
     * Ansi compliant SQL.
     */
    ANSI
}
