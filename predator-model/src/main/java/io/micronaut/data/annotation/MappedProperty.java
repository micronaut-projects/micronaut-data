package io.micronaut.data.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.model.DataType;

import java.lang.annotation.*;

/**
 * Designates a method or field that is mapped as a persistent property. Typically not used directly
 * but as a meta-annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface MappedProperty {

    /**
     * name of the meta-annotation member to store the embedded property configuration.
     */
    String EMBEDDED_PROPERTIES = "embeddedProperties";

    /**
     * The destination the property is persisted to. This could be the column name etc. or some external form.
     *
     * @return The destination
     */
    String value() default "";

    /**
     * @return The data type of the property.
     */
    @AliasFor(annotation = TypeDef.class, member = "type")
    DataType type() default DataType.OBJECT;

    /**
     * Used to define the mapping. For example in the case of SQL this would be the column definition. Example: BLOB NOT NULL.
     *
     * @return A string-based definition of the property type.
     */
    String definition() default "";
}
