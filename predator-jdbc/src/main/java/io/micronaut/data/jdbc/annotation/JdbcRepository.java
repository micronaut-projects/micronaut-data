package io.micronaut.data.jdbc.annotation;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import java.lang.annotation.*;

/**
 * Stereotype repository that configures a {@link Repository} as a {@link JdbcRepository} using
 * raw SQL encoding and {@link JdbcRepositoryOperations} as the runtime engine.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Repository(
    queryBuilder = SqlQueryBuilder.class,
    operations = JdbcRepositoryOperations.class,
    implicitQueries = false
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
public @interface JdbcRepository {
    /**
     * @return The dialect to use.
     */
    Dialect dialect() default Dialect.H2;

    /**
     * The SQL dialect to use.
     */
    enum Dialect {
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
        ORACLE
    }
}
