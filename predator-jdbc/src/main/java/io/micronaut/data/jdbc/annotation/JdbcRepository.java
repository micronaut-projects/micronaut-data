package io.micronaut.data.jdbc.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.jdbc.mapper.SqlResultConsumer;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import java.lang.annotation.*;

/**
 * Stereotype repository that configures a {@link Repository} as a {@link JdbcRepository} using
 * raw SQL encoding and {@link JdbcRepositoryOperations} as the runtime engine.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@RepositoryConfiguration(
    queryBuilder = SqlQueryBuilder.class,
    operations = JdbcRepositoryOperations.class,
    implicitQueries = false,
    namedParameters = false,
    typeRoles = @TypeRole(
            role = SqlResultConsumer.ROLE,
            type = SqlResultConsumer.class
    )
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repository
public @interface JdbcRepository {
    /**
     * @return The dialect to use.
     */
    @AliasFor(annotation = Repository.class, member = "dialect")
    Dialect dialect() default Dialect.ANSI;

    /**
     * @return The dialect to use.
     */
    @AliasFor(annotation = Repository.class, member = "dialect")
    @AliasFor(annotation = JdbcRepository.class, member = "dialect")
    String dialectName() default "ANSI";
}
