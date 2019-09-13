package io.micronaut.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation that specifies the embedded ID.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Documented
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Relation(Relation.Kind.EMBEDDED)
@Id
public @interface EmbeddedId {
}
