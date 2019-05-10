package io.micronaut.data.annotation;

import java.lang.annotation.*;

/**
 * Can be applied to date type to indicate the property should be populated when it is first inserted.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Documented
@GeneratedValue
public @interface DateCreated {
}
