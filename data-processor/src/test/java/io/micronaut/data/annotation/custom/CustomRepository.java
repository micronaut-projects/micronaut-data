package io.micronaut.data.annotation.custom;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.query.builder.custom.CustomQueryBuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom repository used in some test cases.
 */
@RepositoryConfiguration(
        queryBuilder = CustomQueryBuilder.class
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Repository
public @interface CustomRepository {

    @AliasFor(annotation = Repository.class, member = "value")
    String value();
}
