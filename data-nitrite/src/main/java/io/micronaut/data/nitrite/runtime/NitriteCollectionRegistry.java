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
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.annotation.FullTextIndex;
import io.micronaut.data.nitrite.annotation.SpatialIndex;
import io.micronaut.data.nitrite.conf.NitriteConfiguration;
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.index.IndexOptions;
import org.dizitart.no2.index.IndexType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.dizitart.no2.index.IndexOptions.indexOptions;

/**
 * Manages Nitrite collection lookup and index creation.
 * Caches collections and ensures indexes are created once per collection.
 *
 * @since 5.0.0
 */
@Internal
final class NitriteCollectionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(NitriteCollectionRegistry.class);

    private final Nitrite database;
    private final NitriteTransactionHolder transactionHolder;
    private final NitriteConfiguration configuration;
    private final Function<Class<?>, RuntimePersistentEntity<?>> entityFactory;

    private final Set<String> indexedCollections = ConcurrentHashMap.newKeySet();
    private final Map<String, NitriteCollection> collectionCache = new ConcurrentHashMap<>();

    NitriteCollectionRegistry(Nitrite database,
                              NitriteTransactionHolder transactionHolder,
                              NitriteConfiguration configuration,
                              Function<Class<?>, RuntimePersistentEntity<?>> entityFactory) {
        this.database = database;
        this.transactionHolder = transactionHolder;
        this.configuration = configuration;
        this.entityFactory = entityFactory;
    }

    String getCollectionName(Class<?> type) {
        MappedEntity mappedEntity = type.getAnnotation(MappedEntity.class);
        return (mappedEntity != null && !mappedEntity.value().isEmpty()) ? mappedEntity.value() : type.getSimpleName();
    }

    NitriteCollection getCollection(Class<?> type) {
        String name = getCollectionName(type);
        NitriteCollection collection;
        if (transactionHolder.isActive()) {
            // Nitrite transactions require the collection to pre-exist before the transaction started.
            // Touch the collection on the database first (idempotent: creates if absent).
            database.getCollection(name);
            collection = transactionHolder.get().getCollection(name);
        } else {
            collection = collectionCache.computeIfAbsent(name, database::getCollection);
        }
        ensureIndexes(type, collection, name);
        return collection;
    }

    private void ensureIndexes(Class<?> type, NitriteCollection collection, String name) {
        if (!configuration.isCreateIndexes() || indexedCollections.contains(name)) {
            return;
        }
        indexedCollections.add(name);
        RuntimePersistentEntity<?> entity = entityFactory.apply(type);
        List<AnnotationValue<Index>> indexes = entity.getAnnotationMetadata().getAnnotationValuesByType(Index.class);
        for (AnnotationValue<Index> index : indexes) {
            String[] columns = index.getRequiredValue("columns", String[].class);
            String[] translatedColumns = new String[columns.length];
            for (int i = 0; i < columns.length; i++) {
                String col = columns[i];
                RuntimePersistentProperty<?> prop = entity.getPropertyByName(col);
                translatedColumns[i] = prop != null ? prop.getPersistedName() : col;
            }
            boolean unique = index.booleanValue("unique").orElse(false);
            IndexOptions options = indexOptions(unique ? IndexType.UNIQUE : IndexType.NON_UNIQUE);
            try {
                collection.createIndex(options, translatedColumns);
            } catch (Exception e) {
                LOG.warn("Could not create index for collection {}: {}", collection.getName(), e.getMessage());
            }
        }
        for (RuntimePersistentProperty<?> property : entity.getPersistentProperties()) {
            if (property.getAnnotationMetadata().hasAnnotation(Index.class)) {
                AnnotationValue<Index> index = property.getAnnotationMetadata().getAnnotation(Index.class);
                boolean unique = index.booleanValue("unique").orElse(false);
                try {
                    collection.createIndex(indexOptions(unique ? IndexType.UNIQUE : IndexType.NON_UNIQUE), property.getPersistedName());
                } catch (Exception e) {
                    LOG.warn("Could not create index for field {} in collection {}: {}", property.getName(), collection.getName(), e.getMessage());
                }
            }
            if (property.getAnnotationMetadata().hasAnnotation(FullTextIndex.class)) {
                try {
                    collection.createIndex(indexOptions(IndexType.FULL_TEXT), property.getPersistedName());
                } catch (Exception e) {
                    LOG.warn("Could not create full-text index for field {} in collection {}: {}", property.getName(), collection.getName(), e.getMessage());
                }
            }
            if (property.getAnnotationMetadata().hasAnnotation(SpatialIndex.class)) {
                try {
                    collection.createIndex(indexOptions("Spatial"), property.getPersistedName());
                } catch (Exception e) {
                    LOG.warn("Could not create spatial index for field {} in collection {}: {}", property.getName(), collection.getName(), e.getMessage());
                }
            }
        }
        // Note: Do not create a unique index on the "id" field.
        // Nitrite handles document uniqueness internally via its _id field.
        // Creating a unique index on "id" causes constraint violations when
        // multiple documents are inserted rapidly with timestamp-based IDs.
    }
}
