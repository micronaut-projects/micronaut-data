/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import reactor.core.publisher.Flux;

/**
 * The reactive entities operations container.
 *
 * @param <T>   The entity type
 * @param <Exc> The exception
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public abstract class ReactiveEntitiesOperations<T, Exc extends Exception> extends EntitiesOperations<T, Exc> {

    public ReactiveEntitiesOperations(EntityEventListener<Object> entityEventListener, RuntimePersistentEntity<T> persistentEntity, ConversionService conversionService) {
        super(entityEventListener, persistentEntity, conversionService);
    }

    /**
     * @return The entities
     */
    public abstract Flux<T> getEntities();
}
