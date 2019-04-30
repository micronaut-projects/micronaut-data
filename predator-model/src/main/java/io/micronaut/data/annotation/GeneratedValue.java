package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * Designates a property as a generated value. Typically not used
 * directly but instead mapped to via annotation such as {@code javax.persistence.GeneratedValue}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface GeneratedValue {
}
