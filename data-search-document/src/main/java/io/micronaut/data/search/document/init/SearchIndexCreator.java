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

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.data.search.document.conf.DocumentSearchConfiguration;
import io.micronaut.data.search.document.runtime.DocumentSearchRepositoryOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Creates the index of every {@link Searchable} entity on startup when it does not exist.
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
     */
    SearchIndexCreator(List<DocumentSearchRepositoryOperations> operations, RuntimeEntityRegistry runtimeEntityRegistry, DocumentSearchConfiguration configuration) {
        SchemaGenerate schemaGenerate = configuration.getSchemaGenerate();
        if (schemaGenerate == SchemaGenerate.NONE) {
            return;
        }
        for (BeanIntrospection<Object> introspection : BeanIntrospector.SHARED.findIntrospections(MappedEntity.class)) {
            Class<Object> type = introspection.getBeanType();
            if (type.getName().contains("$") || Modifier.isAbstract(type.getModifiers()) || !introspection.hasAnnotation(Searchable.class)) {
                continue;
            }
            RuntimePersistentEntity<Object> entity = runtimeEntityRegistry.getEntity(type);
            for (DocumentSearchRepositoryOperations ops : operations) {
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
    }
}
