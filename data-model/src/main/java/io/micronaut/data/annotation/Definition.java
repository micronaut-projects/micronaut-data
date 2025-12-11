/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SQL type definition override for a particular {@link Dialect}.
 * <p>
 * This is used in conjunction with {@link TypeDef#definitions()} to provide
 * vendor-specific column definitions. For example, to map a logical type
 * to Oracle 23ai VECTOR or PostgreSQL pgvector.
 *
 * @since 5.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface Definition {
    /**
     * The SQL dialect this definition applies to.
     * @return The dialect
     */
    @NonNull
    Dialect dialect();

    /**
     * The logical type name used by the dialect (for example, VECTOR or vector).
     * @return The type name
     */
    @NonNull
    String value();

    /**
     * Optional format string used to render a fully-qualified type definition.
     * The format typically contains placeholders like {@code %d} for dimensions
     * or other parameters, and is interpreted by the schema utilities.
     * @return The format string or empty if not applicable
     */
    String format() default "";
}
