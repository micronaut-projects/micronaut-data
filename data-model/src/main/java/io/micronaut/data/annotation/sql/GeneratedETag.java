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
package io.micronaut.data.annotation.sql;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a mapped property as holding a generated ETag value for optimistic locking.
 * <p>
 * This is analogous to {@code @GeneratedValue} for identifiers but specific to generated ETag values. During
 * annotation processing, the {@code @GeneratedETag} property is synthesized as a versioned, generated read-only
 * property. Its read value is computed by applying a SQL function over a set of properties that participate in the ETag.
 * Those properties are either explicitly marked with {@link ETagValue} or implicitly included when the owning
 * entity is annotated with {@link ETaggable}.
 *
 * The actual mapping to {@code @Version}, {@code @GeneratedValue} and {@code @ColumnTransformer} is performed
 * by the MappedEntityVisitor in the data-processor module during annotation processing.
 *
 * @author radovanradic
 * @since 5.1
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
public @interface GeneratedETag {

    /**
     * Internal marker used by annotation processing when function resolution is deferred to SQL dialect handling.
     */
    String DIALECT_DEFAULT_FUNCTION_MARKER = "__MICRONAUT_DATA_DIALECT_DEFAULT_ETAG_FUNCTION__";

    /**
     * The SQL function name to compute the ETag (e.g. {@code SYS_ROW_ETAG}) using values
     * annotated by {@link ETagValue} or implicitly included via {@link ETaggable}.
     * <p>
     * The value should be a function name, which may be schema-qualified, such as {@code SYS_ROW_ETAG}
     * or {@code MY_SCHEMA.MY_ETAG_FUNCTION}. Do not include parentheses, argument placeholders, or column
     * references; Micronaut Data supplies the resolved ETag input columns.
     * <p>
     * If not specified, the function may be resolved from the SQL dialect at query-build time.
     * Currently, Oracle defaults to {@code SYS_ROW_ETAG}; other dialects require an explicit function.
     *
     * @return function name
     */
    String function() default "";
}
