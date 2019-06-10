package io.micronaut.data.jpa.annotation;

import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Allows configuring JPA 2.1 entity graphs on query methods. Largely based on the same annotation as in Spring Data.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Documented
public @interface EntityGraph {

    /**
     * Same as {@link #name()}.
     * @return The name of the entity graph.
     */
    @AliasFor(member = "name")
    String value() default "";

    /**
     * Specifies the name of the entity graph. If none is specified one will be created at runtime.
     *
     * @return The name of the entity graph to use.
     */
    @AliasFor(member = "value")
    String name() default "";

    /**
     * @return The name of the hint to use.
     * @see <a href="https://download.oracle.com/otn-pub/jcp/persistence-2_1-fr-eval-spec/JavaPersistence.pdf">JPA 2.1
     *      Specification: 3.7.4.2 Load Graph Semantics</a>
     */
    String hint() default "javax.persistence.fetchgraph";

    /**
     * The attributes paths to include in the entity graph.
     * @return The attributes paths
     */
    String[] attributePaths() default {};
}
