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
 * <p>Annotates a repository method returning entities as a parameter-based automatic query method.</p>
 *
 * <p>The {@code Find} annotation indicates that the annotated repository method executes a query to retrieve entities
 * based on its parameters and on the arguments assigned to its parameters. The method return type identifies the entity
 * type returned by the query. Each parameter of the annotated method must either:
 * </p>
 * <ul>
 * <li>have exactly the same type and name (the parameter name in the Java source, or a name assigned by {@link By @By})
 *     as a persistent field or property of the entity class, or</li>
 * </ul>
 * <p>The query is inferred from the method parameters which match persistent fields of the entity.
 * </p>
 * <p>There is no specific naming convention for methods annotated with {@code @Find}; they may be named arbitrarily,
 * and their names do not carry any semantic meaning defined by the Jakarta Data specification.
 * </p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Find {

    /**
     * <p>Optionally specifies the queried entity type.</p>
     *
     * <p>The default value, {@code void.class}, has the special meaning of
     * determining the entity type from the method return type if the method
     * returns entities, and otherwise from the primary entity type of the
     * repository.</p>
     *
     * <p>A repository method with the {@code Find} annotation must specify a
     * valid entity class as the value if the method does not return entities
     * and the repository does not otherwise define a primary entity type.</p>
     *
     * <p>For example,</p>
     *
     * <pre>
     * &#64;Repository
     * public interface Vehicles {
     *     &#64;Find(Car.class)
     *     Optional&lt;Car&gt; findCar(@By(_Car.VIN) String vehicleIdNum);
     *
     *     ...
     * }
     * </pre>
     * @return The entity type
     * @since 5.0
     */
    Class<?> value() default void.class;

}
