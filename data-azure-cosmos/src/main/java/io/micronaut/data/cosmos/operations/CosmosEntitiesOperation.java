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
package io.micronaut.data.cosmos.operations;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntitiesOperations;

/**
 * Base class for Cosmos multiple entities operation (update and delete while insert is done manually calling single insert operation).
 *
 * @since 4.0.0
 * @author radovanradic
 *
 * @param <T> the entity type
 */
public abstract class CosmosEntitiesOperation<T> extends AbstractSyncEntitiesOperations<CosmosEntityOperation.CosmosOperationContext<T>, T, RuntimeException> {

    protected int affectedCount;

    /**
     * Default constructor.
     *
     * @param entityEventListener The entity event listener
     * @param conversionService   The conversion service
     * @param ctx                 The context
     * @param persistentEntity    The persistent entity
     * @param entities            The entities
     */
    protected CosmosEntitiesOperation(EntityEventListener<Object> entityEventListener,
                                      ConversionService<?> conversionService,
                                      CosmosEntityOperation.CosmosOperationContext ctx,
                                      RuntimePersistentEntity<T> persistentEntity,
                                      Iterable<T> entities) {
        super(ctx, null, conversionService, entityEventListener, persistentEntity, entities, false);
    }

    @Override
    protected void cascadePre(Relation.Cascade cascadeType) {
        // No cascade implemented for Cosmos
    }

    @Override
    protected void cascadePost(Relation.Cascade cascadeType) {
        // No cascade implemented for Cosmos
    }
}
