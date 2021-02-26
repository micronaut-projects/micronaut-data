/*
 * Copyright 2017-2021 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.event.EntityEventListener;

/**
 * A registry for looking up entities across repositories.
 *
 * @author graemerocher
 * @since 2.3.0
 */
public interface RuntimeEntityRegistry {
    /**
     * @return The primary entity event listener
     */
    @NonNull EntityEventListener<Object> getEntityEventListener();

    /**
     * Get a new, non-cached instance.
     * @param type The type
     * @param <T> The generic type
     * @return The entity
     */
    @NonNull <T> RuntimePersistentEntity<T> getEntity(@NonNull Class<T> type);

    /**
     * Get a new, non-cached instance.
     * @param type The type
     * @param <T> The generic type
     * @return The entity
     */
    @NonNull <T> RuntimePersistentEntity<T> newEntity(@NonNull Class<T> type);
}
