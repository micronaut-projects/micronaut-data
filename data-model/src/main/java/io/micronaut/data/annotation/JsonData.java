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
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.serde.annotation.Serdeable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation defining JSON model sourced from the database table.
 * It defines table from which JSON data is sourced and permissions for the table (INSERT, UPDATE, DELETE,...).
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Serdeable
@Documented
@Experimental
@EntityRepresentation(type = EntityRepresentation.Type.COLUMN, columnType = EntityRepresentation.ColumnType.JSON)
public @interface JsonData {

    String DEFAULT_COLUMN_NAME = "DATA";

    /**
     * The name of the single column in the view.
     *
     * @return the column name (default DATA)
     */
    @AliasFor(annotation = EntityRepresentation.class, member = "column")
    String column() default DEFAULT_COLUMN_NAME;

    /**
     * @return the table name in the database from where json view is getting the data
     */
    String table() default "";

    /**
     * @return permissions for the table, combination of UPDATE, INSERT, DELETE or NOUPDATE, NOINSERT, NODELETE for the view
     */
    String permissions() default "";
}
