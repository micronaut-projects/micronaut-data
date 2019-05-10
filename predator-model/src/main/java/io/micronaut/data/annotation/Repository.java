package io.micronaut.data.annotation;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.data.intercept.PredatorIntroductionAdvice;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder;

import java.lang.annotation.*;

/**
 * Designates a type of a data repository. If the type is an interface or abstract
 * class this annotation will attempt to automatically provide implementations at compilation time.
 *
 * @author graemerocher
 * @since 1.0
 */
@Introduction
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Type(PredatorIntroductionAdvice.class)
public @interface Repository {
    /**
     * The name of the underlying datasource connection name. In a multiple data source scenario this will
     * be the name of a configured datasource or connection.
     *
     * @return The connection name
     */
    String value() default "";

    /**
     * The builder to use to encode queries. Defaults to JPA-QL.
     *
     * @return The query builder
     */
    Class<? extends QueryBuilder> queryBuilder() default JpaQueryBuilder.class;

    /**
     * The supported pagination types.
     *
     * @return The pagination types
     */
    Class<?>[] paginationTypes() default { Pageable.class };
}
