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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Customizes the join table of a {@code ONE_TO_MANY} or {@code MANY_TO_MANY} association that is stored in a separate
 * table. A join table (also called a link table) has one row per linked pair of entities, holding a foreign key to
 * the owning entity and a foreign key to the associated entity.
 *
 * <p>Without this annotation, the table name and the column names are derived from the entity and association names by
 * the {@link io.micronaut.data.model.naming.NamingStrategy}. Use {@link #name()} and {@link #schema()} to name the
 * table, {@link #joinColumns()} to name the columns that reference the owning entity and
 * {@link #inverseJoinColumns()} to name the columns that reference the associated entity. Place the annotation on the
 * owning side of the association, that is the side without {@code mappedBy}.</p>
 *
 * <p>The JPA annotation {@code jakarta.persistence.JoinTable} can be used instead and is interpreted the same way.</p>
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface JoinTable {
    /**
     * @return The name of the join table
     */
    String name() default "";

    /**
     * @return The join columns to use.
     */
    JoinColumn[] joinColumns() default {};

    /**
     * @return The inverse join columns to use.
     */
    JoinColumn[] inverseJoinColumns() default {};

    /**
     * @return The alias to use for the query
     */
    String alias() default "";

    /**
     * @return The schema of the join table
     */
    String schema() default "";
}
