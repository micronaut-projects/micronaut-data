/*
 * Copyright 2017-2020 original authors
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

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Customizes a foreign key column of an association: the column that stores the ID of the associated entity for a
 * {@code MANY_TO_ONE} or {@code ONE_TO_ONE} association, or one of the columns of a {@link JoinTable}.
 *
 * <p>{@link #name()} is the name of the foreign key column and {@link #referencedColumnName()} the column of the
 * referenced table it points to (the primary key by default). The annotation is repeatable, so a foreign key to an
 * entity with a composite ID can declare one column per ID component.</p>
 *
 * <p>The JPA annotation {@code jakarta.persistence.JoinColumn} can be used instead and is interpreted the same way.</p>
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Repeatable(JoinColumns.class)
public @interface JoinColumn {

    /**
     * The name of the foreign column.
     *
     * @return The name of the foreign column
     */
    String name() default "";

    /**
     * The name of the column referenced by this foreign column.
     *
     * @return The referenced column name
     */
    String referencedColumnName() default "";

    /**
     * Used to define the mapping. For example in the case of SQL this would be the column definition. Example: BLOB NOT NULL.
     *
     * @return A string-based definition of the property type.
     */
    String columnDefinition() default "";

}
