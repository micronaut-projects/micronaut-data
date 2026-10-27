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
package io.micronaut.data.mongodb.init;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.mongodb.conf.RequiresSyncMongo;
import io.micronaut.data.mongodb.operations.MongoCollectionNameProvider;
import io.micronaut.data.mongodb.operations.MongoDatabaseNameProvider;
import io.micronaut.data.runtime.search.SearchIndexOperations;
import jakarta.inject.Singleton;
import org.bson.Document;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link SearchIndexOperations} of MongoDB: the {@code $text} index of a {@code @Searchable} entity, or
 * the search and vector search indexes of a {@code @MongoSearchIndex} one. {@link #sync} waits until the
 * search indexes are queryable, as MongoDB Search builds them asynchronously.
 *
 * @since 5.4.0
 */
@Experimental
@Singleton
@RequiresSyncMongo
@Requires(beans = MongoClient.class)
public final class MongoSearchIndexOperations implements SearchIndexOperations {

    private static final Duration SYNC_TIMEOUT = Duration.ofMinutes(2);

    private final MongoClient mongoClient;
    private final MongoDatabaseNameProvider databaseNameProvider;
    private final MongoCollectionNameProvider collectionNameProvider;
    private final RuntimeEntityRegistry runtimeEntityRegistry;

    MongoSearchIndexOperations(MongoClient mongoClient,
                               MongoDatabaseNameProvider databaseNameProvider,
                               MongoCollectionNameProvider collectionNameProvider,
                               RuntimeEntityRegistry runtimeEntityRegistry) {
        this.mongoClient = mongoClient;
        this.databaseNameProvider = databaseNameProvider;
        this.collectionNameProvider = collectionNameProvider;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public boolean exists(Class<?> entityType) {
        RuntimePersistentEntity<?> entity = runtimeEntityRegistry.getEntity(entityType);
        MongoCollection<Document> collection = collection(entity);
        AbstractMongoCollectionsCreator.SearchIndexDefinition searchIndex = AbstractMongoCollectionsCreator.searchIndexDefinition(entity);
        if (searchIndex != null) {
            Set<String> names = searchIndexNames(collection);
            AbstractMongoCollectionsCreator.VectorIndexDefinition vectorIndex = AbstractMongoCollectionsCreator.vectorIndexDefinition(entity);
            return names.contains(searchIndex.name()) && (vectorIndex == null || names.contains(vectorIndex.name()));
        }
        AbstractMongoCollectionsCreator.TextIndexDefinition textIndex = AbstractMongoCollectionsCreator.textIndexDefinition(entity, collection.getNamespace().getCollectionName());
        return textIndex != null && indexNames(collection).contains(textIndex.name());
    }

    @Override
    public void create(Class<?> entityType) {
        RuntimePersistentEntity<?> entity = runtimeEntityRegistry.getEntity(entityType);
        MongoCollection<Document> collection = collection(entity);
        AbstractMongoCollectionsCreator.SearchIndexDefinition searchIndex = AbstractMongoCollectionsCreator.searchIndexDefinition(entity);
        if (searchIndex != null) {
            Set<String> names = searchIndexNames(collection);
            if (!names.contains(searchIndex.name())) {
                collection.createSearchIndex(searchIndex.name(), searchIndex.definition());
            }
            AbstractMongoCollectionsCreator.VectorIndexDefinition vectorIndex = AbstractMongoCollectionsCreator.vectorIndexDefinition(entity);
            if (vectorIndex != null && !names.contains(vectorIndex.name())) {
                collection.createSearchIndexes(List.of(vectorIndex.model()));
            }
            return;
        }
        AbstractMongoCollectionsCreator.TextIndexDefinition textIndex = AbstractMongoCollectionsCreator.textIndexDefinition(entity, collection.getNamespace().getCollectionName());
        if (textIndex != null && !indexNames(collection).contains(textIndex.name())) {
            collection.createIndex(textIndex.keys(), textIndex.options());
        }
    }

    @Override
    public void drop(Class<?> entityType) {
        RuntimePersistentEntity<?> entity = runtimeEntityRegistry.getEntity(entityType);
        MongoCollection<Document> collection = collection(entity);
        AbstractMongoCollectionsCreator.SearchIndexDefinition searchIndex = AbstractMongoCollectionsCreator.searchIndexDefinition(entity);
        if (searchIndex != null) {
            Set<String> names = searchIndexNames(collection);
            if (names.contains(searchIndex.name())) {
                collection.dropSearchIndex(searchIndex.name());
            }
            AbstractMongoCollectionsCreator.VectorIndexDefinition vectorIndex = AbstractMongoCollectionsCreator.vectorIndexDefinition(entity);
            if (vectorIndex != null && names.contains(vectorIndex.name())) {
                collection.dropSearchIndex(vectorIndex.name());
            }
            return;
        }
        AbstractMongoCollectionsCreator.TextIndexDefinition textIndex = AbstractMongoCollectionsCreator.textIndexDefinition(entity, collection.getNamespace().getCollectionName());
        if (textIndex != null && indexNames(collection).contains(textIndex.name())) {
            collection.dropIndex(textIndex.name());
        }
    }

    @Override
    public void sync(Class<?> entityType) {
        RuntimePersistentEntity<?> entity = runtimeEntityRegistry.getEntity(entityType);
        if (AbstractMongoCollectionsCreator.searchIndexDefinition(entity) == null) {
            // a $text index is maintained with the writes
            return;
        }
        MongoCollection<Document> collection = collection(entity);
        long deadline = System.nanoTime() + SYNC_TIMEOUT.toNanos();
        while (true) {
            boolean ready = true;
            for (Document index : collection.listSearchIndexes()) {
                if (!"READY".equals(index.getString("status")) || !Boolean.TRUE.equals(index.getBoolean("queryable"))) {
                    ready = false;
                }
            }
            if (ready) {
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new DataAccessException("The search indexes of " + entityType.getName() + " did not become queryable within " + SYNC_TIMEOUT);
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DataAccessException("Interrupted while waiting for the search indexes of " + entityType.getName(), e);
            }
        }
    }

    private MongoCollection<Document> collection(RuntimePersistentEntity<?> entity) {
        return mongoClient.getDatabase(databaseNameProvider.provide(entity)).getCollection(collectionNameProvider.provide(entity));
    }

    private static Set<String> searchIndexNames(MongoCollection<Document> collection) {
        Set<String> names = new HashSet<>();
        for (Document index : collection.listSearchIndexes()) {
            names.add(index.getString("name"));
        }
        return names;
    }

    private static Set<String> indexNames(MongoCollection<Document> collection) {
        Set<String> names = new HashSet<>();
        for (Document index : collection.listIndexes()) {
            names.add(index.getString("name"));
        }
        return names;
    }
}
