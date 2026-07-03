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
package io.micronaut.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Lifecycle annotation for repository methods which perform upsert operations.</p>
 *
 * <p>The {@code Upsert} annotation indicates that the annotated repository method adds the state of one or more
 * entities to the database when missing, or updates the existing database state when present.
 * </p>
 * <p>An {@code Upsert} method accepts an instance or instances of an entity class. The method must have exactly one
 * parameter whose type is either:
 * </p>
 * <ul>
 *     <li>the class of the entity to be upserted, or</li>
 *     <li>{@code Iterable<E>} where {@code E} is the class of the entities to be upserted.</li>
 * </ul>
 * <p>The annotated method may be declared {@code void}, return a number type, return the entity type, or return an
 * iterable/reactive/asynchronous container producing the entity type, depending on the repository type.
 * </p>
 * <p>By default, the entity identity is used to determine whether an existing row should be updated. The
 * {@link #conflictsOn()} member can be used to select a different property or set of properties as the conflict
 * target.
 * </p>
 *
 * @since 5.1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Upsert {

    /**
     * The persistent entity properties to use as the conflict target.
     *
     * @return The conflict target properties
     */
    String[] conflictsOn() default {};
}
