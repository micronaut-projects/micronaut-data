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
package io.micronaut.data.age.operations;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.age.annotation.AgeRepository;
import io.micronaut.data.graph.runtime.GraphRepositoryEntities;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Set;

/**
 * Creates the indexes of the entities of Apache AGE repositories at startup when
 * {@code micronaut.data.age.create-indexes} is {@code true}, in the graph of the data source of each repository.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Singleton
@Requires(property = AgeIndexCreator.CREATE_INDEXES, value = StringUtils.TRUE)
@Internal
final class AgeIndexCreator implements ApplicationEventListener<StartupEvent> {

    static final String CREATE_INDEXES = "micronaut.data.age.create-indexes";

    private final BeanContext beanContext;

    AgeIndexCreator(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        for (Map.Entry<String, Set<Class<?>>> entry : GraphRepositoryEntities.byDataSource(beanContext, AgeRepository.class).entrySet()) {
            AgeRepositoryOperations operations = beanContext.getBean(AgeRepositoryOperations.class, Qualifiers.byName(entry.getKey()));
            if (operations instanceof DefaultAgeRepositoryOperations defaultOperations) {
                defaultOperations.createIndexes(entry.getValue());
            }
        }
    }
}
