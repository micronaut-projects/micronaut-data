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
package io.micronaut.data.mongodb.init;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.Environment;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.mongodb.conf.MongoDataConfiguration;
import io.micronaut.data.mongodb.conf.RequiresSyncMongo;
import io.micronaut.data.mongodb.operations.MongoCollectionNameProvider;
import io.micronaut.data.mongodb.operations.MongoDatabaseNameProvider;
import jakarta.annotation.PostConstruct;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MongoDB's collections creator.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Context
@Internal
@RequiresSyncMongo
@Requires(property = MongoDataConfiguration.PREFIX)
public final class MongoCollectionsCreator extends AbstractMongoCollectionsCreator<MongoDatabase> {

    @PostConstruct
    void initialize(BeanLocator beanLocator,
                    RuntimeEntityRegistry runtimeEntityRegistry,
                    Environment environment,
                    List<AbstractMongoConfiguration> mongoConfigurations,
                    MongoDataConfiguration mongoDataConfiguration,
                    MongoCollectionNameProvider mongoCollectionNameProvider) {

        super.initialize(runtimeEntityRegistry, environment, mongoConfigurations, mongoDataConfiguration, mongoConfiguration -> {
            MongoClient mongoClient = getMongoFactory(MongoClient.class, beanLocator, mongoConfiguration);
            MongoDatabaseNameProvider mongoDatabaseNameProvider = getMongoFactory(MongoDatabaseNameProvider.class, beanLocator, mongoConfiguration);
            Map<String, Set<String>> databaseCollections = new HashMap<>();
            return new DatabaseOperations<>() {

                @Override
                public String getDatabaseName(MongoDatabase database) {
                    return database.getName();
                }

                @Override
                public MongoDatabase find(PersistentEntity persistentEntity) {
                    return mongoClient.getDatabase(mongoDatabaseNameProvider.provide(persistentEntity));
                }

                @Override
                public Set<String> listCollectionNames(MongoDatabase database) {
                    return databaseCollections.computeIfAbsent(database.getName(), s -> new HashSet<>(CollectionUtils.iterableToSet(database.listCollectionNames())));
                }

                @Override
                public void createCollection(MongoDatabase database, String collection, @Nullable MongoResolvedCollectionOptions options) {
                    if (options == null) {
                        database.createCollection(collection);
                        return;
                    }
                    database.createCollection(collection, toCreateCollectionOptions(options));
                }

                @Override
                public @Nullable MongoResolvedCollectionOptions getCollectionOptions(MongoDatabase database, String collection) {
                    for (Document collectionDocument : database.listCollections()) {
                        if (!collection.equals(collectionDocument.getString("name"))) {
                            continue;
                        }
                        return toResolvedCollectionOptions(collectionDocument);
                    }
                    return null;
                }

                @Override
                public List<MongoResolvedIndex> listIndexes(MongoDatabase database, String collection) {
                    MongoCollection<Document> mongoCollection = database.getCollection(collection);
                    List<MongoResolvedIndex> indexes = new ArrayList<>();
                    for (Document indexDocument : mongoCollection.listIndexes()) {
                        MongoResolvedIndex resolvedIndex = toResolvedIndex(indexDocument);
                        if (resolvedIndex != null) {
                            indexes.add(resolvedIndex);
                        }
                    }
                    return indexes;
                }

                @Override
                public void createIndex(MongoDatabase database, String collection, MongoResolvedIndex index) {
                    MongoCollection<Document> mongoCollection = database.getCollection(collection);
                    if (index.comment() != null || index.commitQuorum() != null) {
                        database.runCommand(toCreateIndexesCommandDocument(collection, index));
                        return;
                    }
                    mongoCollection.createIndex(index.keysDocument(), toIndexOptions(index));
                }
            };
        }, mongoCollectionNameProvider);
    }
}
