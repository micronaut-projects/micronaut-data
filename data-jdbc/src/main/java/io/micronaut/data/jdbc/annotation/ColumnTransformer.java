package io.micronaut.data.jdbc.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.DataTransformer;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Inspired by Hibernate's annotation of the same name. This annotation allows
 * for a custom SQL expression when reading and writing a property.
 *
 * <p>The write expression must contain exactly one '?' placeholder for the value.</p>
 *
 * <p>For example: <code>read="decrypt(payment_.credit_card_num)" write="encrypt(?)"</code></p>
 *
 * @author graemerocher
 * @since 1.0
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
public @interface ColumnTransformer {
    /**
     * A SQL expression used to the read the column. Note that to reference a column you must use the appropriate alias prefix,
     * which is the table name followed by an underscore. For example for an entity called <code>Project</code> the alias would be <code>project_</code>.
     * @return A SQL expression used to read the column.
     */
    @AliasFor(annotation = DataTransformer.class, member = "read")
    String read() default "";

    /**
     * A SQL expression used to write the column. Must have exactly one '?' placeholder.
     *
     * @return The expression
     */
    @AliasFor(annotation = DataTransformer.class, member = "write")
    String write() default "";
}
