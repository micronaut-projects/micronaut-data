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
package io.micronaut.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Lifecycle annotation for repository methods which perform delete operations; alternatively, annotates a repository
 * method as a parameter-based automatic query method which deletes entities.</p>
 *
 * <p>The {@code Delete} annotation indicates that the annotated repository method deletes the state of one or more
 * entities from the database. It may be used in one of two ways: as a lifecycle annotation, to delete a given entity
 * instance or instances, or as an automatic query annotation, to delete all entities satisfying parameter-based
 * conditions.
 * </p>
 *
 * <p>A {@code Delete} method might accept an instance or instances of an entity class. In this case, the method must
 * have exactly one parameter whose type is either:
 * </p>
 * <ul>
 *     <li>the class of the entity to be deleted, or</li>
 *     <li>{@code List<E>} or {@code E[]} where {@code E} is the class of the entities to be deleted.</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Delete {
}
