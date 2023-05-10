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

import io.micronaut.data.model.JsonDataType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines entity representation for database operations. Is type is TABULAR it means default entity representation
 * and COLUMN will mean result will contain single column with the value of given type.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Inherited
public @interface EntityRepresentation {

    /**
     * The default column name for the JSON data.
     */
    String DEFAULT_COLUMN = "DATA";

    /**
     * @return The query result type
     */
    Type type();

    /**
     * @return the column type if type is {@link Type#COLUMN}
     */
    ColumnType columnType() default ColumnType.JSON;

    /**
     * @return The column containing result if type is {@link Type#COLUMN}
     */
    String column() default DEFAULT_COLUMN;

    /**
     * @return The JSON data type that resulting column will hold if type is {@link Type#COLUMN} and columnType is {@link ColumnType#JSON}.
     * It helps to pick proper column reader based on result data type
     */
    JsonDataType jsonDataType() default JsonDataType.DEFAULT;

    /**
     * Supported entity representation types.
     */
    enum Type {
        /**
         * Default query result.
         */
        TABULAR,
        /**
         * Result when query will produce single column. Could be used for JSON or XML for example.
         */
        COLUMN
    }

    /**
     * The column type. Support only JSON for now.
     */
    enum ColumnType {
        /**
         * JSON entity representation in a column.
         */
        JSON
    }
}
