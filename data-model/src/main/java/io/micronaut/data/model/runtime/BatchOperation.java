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
package io.micronaut.data.model.runtime;

/**
 * A batch operation is an operation performed on one or more entities of the same type.
 *
 * @param <E> The entity type
 * @author graemerocher
 * @since 1.0.0
 */
public interface BatchOperation<E> extends EntityOperation<E>, Iterable<E> {

    /**
     * @return Whether the operation applies to all and not use the iterable values.
     */
    default boolean all() {
        return false;
    }
}
