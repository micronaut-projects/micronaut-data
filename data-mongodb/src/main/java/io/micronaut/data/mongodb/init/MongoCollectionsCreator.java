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
import com.mongodb.client.MongoDatabase;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.mongodb.conf.MongoDataConfiguration;
import io.micronaut.data.mongodb.conf.RequiresSyncMongo;
import io.micronaut.data.mongodb.operations.MongoDatabaseNameProvider;
import jakarta.annotation.PostConstruct;

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
@Requires(property = MongoDataConfiguration.CREATE_COLLECTIONS_PROPERTY, value = StringUtils.TRUE)
public final class MongoCollectionsCreator extends AbstractMongoCollectionsCreator<MongoDatabase> {

    @PostConstruct
    void initialize(BeanLocator beanLocator,
                    RuntimeEntityRegistry runtimeEntityRegistry,
                    List<AbstractMongoConfiguration> mongoConfigurations) {

        super.initialize(runtimeEntityRegistry, mongoConfigurations, mongoConfiguration -> {
            MongoClient mongoClient = getMongoFactory(MongoClient.class, beanLocator, mongoConfiguration);
            MongoDatabaseNameProvider mongoDatabaseNameProvider = getMongoFactory(MongoDatabaseNameProvider.class, beanLocator, mongoConfiguration);
            Map<String, Set<String>> databaseCollections = new HashMap<>();
            return new DatabaseOperations<MongoDatabase>() {

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
                public void createCollection(MongoDatabase database, String collection) {
                    database.createCollection(collection);
                }
            };
        });
    }

}
