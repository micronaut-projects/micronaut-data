package io.micronaut.data.annotation;

import io.micronaut.aop.Introduction;
import io.micronaut.data.model.query.encoder.JpaQueryEncoder;
import io.micronaut.data.model.query.encoder.QueryEncoder;

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
public @interface Repository {
    /**
     * The name of the underlying datasource connection.
     *
     * @return The connection name
     */
    String value() default "";

    /**
     * The encoder to use to encode queries. Defaults to JPA-QL.
     *
     * @return The query encoder
     */
    Class<? extends QueryEncoder> queryEncoder() default JpaQueryEncoder.class;
}
