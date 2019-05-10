package io.micronaut.data.annotation;

import java.lang.annotation.*;
import java.time.OffsetDateTime;

/**
 * Can be applied to date type to indicate the property should be populated when it was last updated.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Documented
@GeneratedValue
@ParameterRole(role = ParameterRole.LAST_UPDATED_PROPERTY, type = OffsetDateTime.class)
public @interface DateUpdated {
}
