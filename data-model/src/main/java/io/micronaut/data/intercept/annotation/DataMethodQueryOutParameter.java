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
package io.micronaut.data.intercept.annotation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.DataType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Internal annotation representing OUT parameter binding metadata for queries
 * (e.g. Oracle RETURNING ... INTO ...).
 *
 * This mirrors a subset of {@link DataMethodQueryParameter} members that are
 * relevant for OUT parameters.
 *
 * @since 5.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Internal
@Inherited
public @interface DataMethodQueryOutParameter {

    /**
     * The member name that holds an optional out parameter name (typically a column name).
     */
    String META_MEMBER_NAME = "name";

    /**
     * The member name that holds the data type.
     */
    String META_MEMBER_DATA_TYPE = "dataType";

    /**
     * @return The OUT parameter name (column/alias), when present.
     */
    String name() default "";

    /**
     * @return The OUT parameter data type (if known).
     */
    DataType dataType() default DataType.OBJECT;
}
