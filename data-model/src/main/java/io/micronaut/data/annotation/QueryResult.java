/*
 * Copyright 2017-2023 original authors
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines query result for database query execution. Is query result type is TABULAR it means default query result
 * and JSON will mean result will contain single column with the JSON value.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
@Inherited
public @interface QueryResult {

    /**
     * The default column name for the JSON data.
     */
    String DATA_COLUMN = "DATA";

    /**
     * @return The column containing JSON result
     */
    String column() default DATA_COLUMN;

    /**
     * @return The query result type
     */
    Type type();

    /**
     * Supported query result types.
     */
    enum Type {
        /**
         * Default query result.
         */
        TABULAR,
        /**
         * JSON result when query will produce single column with JSON value.
         */
        JSON
    }
}
