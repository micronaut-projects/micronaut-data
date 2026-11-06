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
package io.micronaut.data.neo4j.operations;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.graph.runtime.GraphRepositoryEntities;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import jakarta.inject.Singleton;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Creates the indexes and unique constraints of the entities of Neo4j repositories at startup when
 * {@code micronaut.data.neo4j.create-indexes} is {@code true}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Singleton
@Requires(property = Neo4jIndexCreator.CREATE_INDEXES, value = StringUtils.TRUE)
@Internal
final class Neo4jIndexCreator implements ApplicationEventListener<StartupEvent> {

    static final String CREATE_INDEXES = "micronaut.data.neo4j.create-indexes";

    private final BeanContext beanContext;
    private final DefaultNeo4jRepositoryOperations operations;

    Neo4jIndexCreator(BeanContext beanContext, DefaultNeo4jRepositoryOperations operations) {
        this.beanContext = beanContext;
        this.operations = operations;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Set<Class<?>> entities = new LinkedHashSet<>();
        GraphRepositoryEntities.byDataSource(beanContext, Neo4jRepository.class).values().forEach(entities::addAll);
        operations.createIndexes(entities);
    }
}
