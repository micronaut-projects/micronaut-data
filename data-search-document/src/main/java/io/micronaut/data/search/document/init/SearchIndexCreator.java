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
package io.micronaut.data.search.document.init;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.DocumentSearchRepositoryOperations;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates on startup the index of every entity a document search repository stores, so each index
 * has explicit keyword mappings rather than dynamic ones.
 *
 * @since 5.4.0
 */
@Context
@Internal
@Requires(property = DocumentSearchConfiguration.CREATE_INDEXES_PROPERTY, notEquals = StringUtils.FALSE)
@Requires(beans = DocumentSearchRepositoryOperations.class)
public final class SearchIndexCreator {

    private static final Logger LOG = LoggerFactory.getLogger(SearchIndexCreator.class);

    /**
     * @param operations Every document search operations bean
     * @param runtimeEntityRegistry The entity registry
     * @param configuration The configuration
     * @param beanContext The bean context, to find the document search repositories
     */
    SearchIndexCreator(List<DocumentSearchRepositoryOperations> operations,
                       RuntimeEntityRegistry runtimeEntityRegistry,
                       DocumentSearchConfiguration configuration,
                       BeanContext beanContext) {
        SchemaGenerate schemaGenerate = configuration.getSchemaGenerate();
        if (schemaGenerate == SchemaGenerate.NONE) {
            return;
        }
        for (Class<?> type : indexedTypes(beanContext)) {
            RuntimePersistentEntity<?> entity = runtimeEntityRegistry.getEntity(type);
            for (DocumentSearchRepositoryOperations ops : operations) {
                createIndex(ops, entity, schemaGenerate);
            }
        }
    }

    /**
     * @param beanContext The bean context
     * @return The root entities of the document search repositories
     */
    private static Set<Class<?>> indexedTypes(BeanContext beanContext) {
        // only the entities of document search repositories: a @Searchable entity of a SQL or MongoDB repository has no index here
        Set<Class<?>> types = new LinkedHashSet<>();
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(Qualifiers.byStereotype(Repository.class))) {
            boolean documentStore = definition.classValue(RepositoryConfiguration.class, "operations")
                .filter(DocumentSearchRepositoryOperations.class::isAssignableFrom)
                .isPresent();
            if (documentStore) {
                for (ExecutableMethod<?, ?> method : definition.getExecutableMethods()) {
                    method.classValue(DataMethod.class, DataMethod.META_MEMBER_ROOT_ENTITY).ifPresent(types::add);
                }
            }
        }
        return types;
    }

    private static void createIndex(DocumentSearchRepositoryOperations ops, RuntimePersistentEntity<?> entity, SchemaGenerate schemaGenerate) {
        Class<?> type = entity.getIntrospection().getBeanType();
        String index = ops.getIndexName(type);
        boolean exists = ops.getTransport().indexExists(index);
        if (exists && schemaGenerate == SchemaGenerate.CREATE_DROP) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Dropping search index: {} for entity: {}", index, type.getName());
            }
            ops.getTransport().deleteIndex(index);
            exists = false;
        }
        if (!exists) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Creating search index: {} for entity: {}", index, type.getName());
            }
            ops.getTransport().createIndex(index, SearchIndexDefinitions.mappingFor(entity, ops.getTransport()));
        }
    }
}
