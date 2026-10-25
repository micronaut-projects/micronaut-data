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
package io.micronaut.data.graph.runtime;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the entities of the graph repositories of a database, grouped by the name of the data source or driver each
 * repository uses.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class GraphRepositoryEntities {

    private GraphRepositoryEntities() {
    }

    /**
     * The root entities of the repositories annotated with a repository stereotype, such as {@code @Neo4jRepository}.
     *
     * @param beanContext The bean context
     * @param stereotype  The repository annotation
     * @return The entity types by data source name
     */
    public static Map<String, Set<Class<?>>> byDataSource(BeanContext beanContext, Class<? extends Annotation> stereotype) {
        Map<String, Set<Class<?>>> entities = new LinkedHashMap<>();
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(Qualifiers.byStereotype(stereotype))) {
            List<Argument<?>> typeArguments = definition.getTypeArguments(GenericRepository.class);
            if (typeArguments.isEmpty() || typeArguments.get(0).getType() == Object.class) {
                continue;
            }
            String dataSource = definition.stringValue(Repository.class).filter(name -> !name.isEmpty()).orElse("default");
            entities.computeIfAbsent(dataSource, name -> new LinkedHashSet<>()).add(typeArguments.get(0).getType());
        }
        return entities;
    }
}
