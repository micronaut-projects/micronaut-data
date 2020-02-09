package io.micronaut.data.cql.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.cql.operations.CqlRepositoryOperations;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import javax.inject.Named;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@RepositoryConfiguration(
    queryBuilder = SqlQueryBuilder.class,
    operations = CqlRepositoryOperations.class,
    implicitQueries = false,
    namedParameters = false
//    typeRoles = @TypeRole(
//        role = SqlResultConsumer.ROLE,
//        type = SqlResultConsumer.class
//    )
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repository
@Named
public @interface CqlRepository {

    @AliasFor(annotation = Named.class, member = "value")
    String value() default "default";
}
