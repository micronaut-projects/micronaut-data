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
import io.micronaut.data.annotation.JsonView.Operation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotation defining Json Duality SubView. Currently supported only by Oracle database.
 * A JsonView can contain JsonSubViews, which have the same structure as JsonViews.
 * Since only JsonView creation scripts are generated, we need both annotations to differ them.
 *
 * <pre>
 * {@code
 * @JsonSubView(entity = Class.class, operations = { JsonView.Operation.UPDATE, JsonView.Operation.INSERT })
 * public class TeacherScheduleSubView {
 *     \@Id
 *     \@GeneratedValue(GeneratedValue.Type.IDENTITY)
 *     \@MappedProperty(value = "id")
 *     private Long classID;
 *
 *     private String name;
 * }
 * }
 * </pre>
 *
 * @see io.micronaut.data.annotation.JsonView
 * @author dimitrijezravkovic
 * @since 5.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD})
@Serdeable
@Documented
@Experimental
@MappedEntity
@EntityRepresentation(type = EntityRepresentation.Type.COLUMN, columnType = EntityRepresentation.ColumnType.JSON)
public @interface JsonSubView {

    String DEFAULT_COLUMN_NAME = "DATA";

    /**
     * The entity class.
     * Specify an entity class annotated with {@link MappedEntity} that this JSON sub view corresponds to.
     * Valid entity class is one that defines the properties used in this class.
     *
     * @return the entity class (default void)
     */
    Class<?> entity() default void.class;

    /**
     * The supported sql operations array.
     *
     * @return the supported operations array (default [UPDATE, INSERT, DELETE])
     */
    JsonView.Operation[] operations() default {Operation.INSERT, Operation.UPDATE, Operation.DELETE};

    /**
     * The name of the single column in the view.
     *
     * @return the column name (default DATA)
     */
    @AliasFor(annotation = EntityRepresentation.class, member = "column")
    String column() default DEFAULT_COLUMN_NAME;

    /**
     * The Json View name in the database.
     *
     * @return the json view
     */
    @AliasFor(annotation = MappedEntity.class, member = "value")
    String value() default "";

    /**
     * Only applies to supported databases.
     *
     * @return the schema to use for the query
     */
    @AliasFor(annotation = MappedEntity.class, member = "schema")
    String schema() default "";

    /**
     * @return The view alias to use for the query
     */
    @AliasFor(annotation = MappedEntity.class, member = "alias")
    String alias() default "";
}
