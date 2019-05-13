package io.micronaut.data.annotation;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.data.intercept.PredatorIntroductionAdvice;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.query.Sort;
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
     * Configures {@link TypeRole} behaviour for a repository. This member allows for configuration of
     * custom types that play different roles in the construction and execution of repositories. Note that
     * additional {@link io.micronaut.core.convert.TypeConverter} instances may need to be registered if types
     * that do not extend from the defaults are registered.
     *
     * @return The parameter roles
     */
    TypeRole[] typeRoles() default {
        @TypeRole(role = TypeRole.PAGEABLE, type = Pageable.class),
        @TypeRole(role = TypeRole.SORT, type = Sort.class),
        @TypeRole(role = TypeRole.SLICE, type = Slice.class),
        @TypeRole(role = TypeRole.PAGE, type = Page.class)
    };
}
