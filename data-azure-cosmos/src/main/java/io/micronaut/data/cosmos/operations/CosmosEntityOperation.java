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

import com.azure.cosmos.CosmosContainer;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntityOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;

/**
 * Base class for Cosmos entity operation (insert, update and delete).
 *
 * @since 4.0.0
 * @author radovanradic
 *
 * @param <T> the entity type
 */
public abstract class CosmosEntityOperation<T> extends AbstractSyncEntityOperations<CosmosEntityOperation.CosmosOperationContext<T>, T, RuntimeException> {

    protected int affectedCount;

    /**
     * Default constructor.
     *
     * @param entityEventListener The entity event listener
     * @param conversionService   The conversion service
     * @param ctx                 The context
     * @param persistentEntity    The persistent entity
     * @param entity              The entity
     * @param insert              The insert
     */
    protected CosmosEntityOperation(EntityEventListener<Object> entityEventListener,
                                    ConversionService<?> conversionService,
                                    CosmosOperationContext<T> ctx,
                                    RuntimePersistentEntity<T> persistentEntity,
                                    T entity,
                                    boolean insert) {
        super(ctx, null, entityEventListener, persistentEntity, conversionService, entity, insert);
    }

    @Override
    protected void cascadePre(Relation.Cascade cascadeType) {
        // No cascade in Cosmos for now
    }

    @Override
    protected void cascadePost(Relation.Cascade cascadeType) {
        // No cascade in Cosmos for now
    }

    /**
     * The Cosmos Db operation context.
     *
     * @param <T> the entity type
     */
    protected static class CosmosOperationContext<T> extends OperationContext {

        private final CosmosContainer container;
        private final Class<T> rootEntity;

        public CosmosOperationContext(AnnotationMetadata annotationMetadata, Class<?> repositoryType, CosmosContainer container, Class<T> rootEntity) {
            super(annotationMetadata, repositoryType);
            this.container = container;
            this.rootEntity = rootEntity;
        }

        /**
         * @return gets the container in which operation is executing
         */
        public CosmosContainer getContainer() {
            return container;
        }

        /**
         * @return the root entity class
         */
        public Class<T> getRootEntity() {
            return rootEntity;
        }
    }
}
