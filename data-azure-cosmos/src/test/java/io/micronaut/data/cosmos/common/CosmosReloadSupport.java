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
package io.micronaut.data.cosmos.common;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;

/**
 * Reaches the classes of a generation for {@code CosmosEntityReloadSpec}. It is Java, so that no call site cache or
 * class info of Groovy keeps a class of the generation reachable after the test is done with it.
 */
final class CosmosReloadSupport {

    private CosmosReloadSupport() {
    }

    /**
     * Creates the Cosmos entity of a document class of the context's generation, as the database initializer does.
     *
     * @param context The context of the generation
     * @param className The document class
     * @return The partition key of the Cosmos entity
     */
    static String initialize(ApplicationContext context, String className) {
        return CosmosEntity.create(entity(context, className), null).getPartitionKey();
    }

    /**
     * @param context The context of the generation
     * @param className The document class
     * @return The persistent entity the entity registry of the context holds for it
     */
    static RuntimePersistentEntity<?> entity(ApplicationContext context, String className) {
        try {
            Class<?> type = Class.forName(className, true, context.getClassLoader());
            return context.getBean(RuntimeEntityRegistry.class).getEntity(type);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " is not in the application", e);
        }
    }

    /**
     * @param context The context of the generation
     * @param className The document class
     * @return The partition key of the Cosmos entity held for the persistent entity of the class, or null if none is
     */
    static String partitionKey(ApplicationContext context, String className) {
        try {
            return CosmosEntity.get(entity(context, className)).getPartitionKey();
        } catch (NullPointerException e) {
            return null;
        }
    }
}
