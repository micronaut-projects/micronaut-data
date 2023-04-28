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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation defining Json Duality View. Currently supported only by Oracle database.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD})
@Serdeable
@Introspected
@Documented
public @interface JsonView {

    String DEFAULT_COLUMN_NAME = "DATA";

    /**
     * The name of the single column in the view.
     *
     * @return the column name (default DATA)
     */
    String column() default DEFAULT_COLUMN_NAME;

    /**
     * The main table from which view is sourced from.
     * @return the main table for the view, other tables can be joined via embedded mappings
     */
    String table();

    /**
     * @return permissions for the table, combination of UPDATE, INSERT, DELETE or NOUPDATE, NOINSERT, NODELETE for the view
     */
    String permissions() default "";
}
