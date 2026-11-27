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
package io.micronaut.data.graph.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets the direction a path or relationship finder traverses relationships in, overriding the direction of the
 * relationship properties it names. For example, {@code @PathDirection(Direction.UNDIRECTED)} finds paths that follow
 * relationships either way.
 *
 * <p>On a parameter of type {@link Direction} the direction is chosen by the caller, overriding the direction
 * set on the method: {@code existsPathByFollows(Person from, Person to, @PathDirection Direction direction)}.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Documented
public @interface PathDirection {

    /**
     * @return The direction, when used on a method
     */
    Direction value() default Direction.UNDIRECTED;
}
