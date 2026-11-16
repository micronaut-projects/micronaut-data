/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a property of a {@link JsonView} or {@link JsonSubView} as a generated field.
 *
 * <p>A generated field is computed by the database when the document is read and is not mapped to a column.
 * Values sent for a generated field on insert or update are ignored. Exactly one of {@link #path()} or
 * {@link #sql()} must be set.</p>
 *
 * <pre>
 * {@code
 * @JsonView(entity = Product.class, hidden = {"price", "quantity"})
 * public record ProductView(
 *     @Id Long id,
 *     String name,
 *     @JsonViewGenerated(path = "$.price * $.quantity") Double total
 * ) {}
 * }
 * </pre>
 *
 * <p>Currently supported only by Oracle Database, in releases that support generated fields in duality views.</p>
 *
 * @author graemerocher
 * @since 5.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Documented
@Experimental
public @interface JsonViewGenerated {

    /**
     * A SQL/JSON path expression evaluated against the document, for example {@code $.price * $.quantity}.
     * It can reference hidden fields.
     *
     * @return the path expression
     */
    String path() default "";

    /**
     * A SQL expression evaluated against the table of the view, for example {@code UPPER(@.name)}.
     * Use the {@code @.} prefix to refer to columns of the table, it is replaced with the table alias.
     *
     * @return the SQL expression
     */
    String sql() default "";
}
