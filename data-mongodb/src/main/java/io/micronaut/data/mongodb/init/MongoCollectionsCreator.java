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
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.IndexOptions;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.Environment;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
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
                public void createCollection(MongoDatabase database, String collection) {
                    database.createCollection(collection);
                }

                @Override
                public List<MongoResolvedIndex> listIndexes(MongoDatabase database, String collection) {
                    MongoCollection<Document> mongoCollection = database.getCollection(collection);
                    List<MongoResolvedIndex> indexes = new ArrayList<>();
                    for (Document indexDocument : mongoCollection.listIndexes()) {
                        Document keyDocument = indexDocument.get("key", Document.class);
                        if (keyDocument == null || (keyDocument.size() == 1 && keyDocument.getInteger("_id", 0) == 1)) {
                            continue;
                        }
                        List<MongoResolvedIndexField> fields = new ArrayList<>(keyDocument.size());
                        for (Map.Entry<String, Object> entry : keyDocument.entrySet()) {
                            Object value = entry.getValue();
                            if (value instanceof Number number) {
                                fields.add(new MongoResolvedIndexField(entry.getKey(), number.intValue(), null, null, null, null));
                            } else {
                                fields.add(new MongoResolvedIndexField(entry.getKey(), null, null, value.toString(), null, null));
                            }
                        }
                        indexes.add(new MongoResolvedIndex(
                                indexDocument.getString("name"),
                                List.copyOf(fields),
                                indexDocument.getBoolean("unique", false),
                                indexDocument.getBoolean("sparse", false),
                                indexDocument.getInteger("expireAfterSeconds"),
                                indexDocument.get("partialFilterExpression") == null ? null : indexDocument.get("partialFilterExpression").toString(),
                                indexDocument.get("collation") == null ? null : indexDocument.get("collation").toString(),
                                null,
                                null,
                                null,
                                indexDocument.get("wildcardProjection") == null ? null : indexDocument.get("wildcardProjection").toString()
                        ));
                    }
                    return indexes;
                }

                @Override
                public void createIndex(MongoDatabase database, String collection, MongoResolvedIndex index) {
                    MongoCollection<Document> mongoCollection = database.getCollection(collection);
                    IndexOptions indexOptions = new IndexOptions().unique(index.unique()).sparse(index.sparse());
                    if (index.name() != null) {
                        indexOptions.name(index.name());
                    }
                    if (index.expireAfterSeconds() != null) {
                        indexOptions.expireAfter((long) index.expireAfterSeconds(), java.util.concurrent.TimeUnit.SECONDS);
                    }
                    if (index.partialFilterExpression() != null) {
                        indexOptions.partialFilterExpression(Document.parse(index.partialFilterExpression()));
                    }
                    if (index.collation() != null) {
                        indexOptions.collation(toCollation(Document.parse(index.collation())));
                    }
                    if (index.bits() != null) {
                        indexOptions.bits(index.bits());
                    }
                    if (index.min() != null) {
                        indexOptions.min(index.min());
                    }
                    if (index.max() != null) {
                        indexOptions.max(index.max());
                    }
                    mongoCollection.createIndex(index.keysDocument(), indexOptions);
                }
            };
        }, mongoCollectionNameProvider);
    }


    private Collation toCollation(Document document) {
        Collation.Builder builder = Collation.builder();
        String locale = document.getString("locale");
        if (locale != null) {
            builder.locale(locale);
        }
        Integer strength = document.getInteger("strength");
        if (strength != null) {
            builder.collationStrength(CollationStrength.fromInt(strength));
        }
        Boolean caseLevel = document.getBoolean("caseLevel");
        if (caseLevel != null) {
            builder.caseLevel(caseLevel);
        }
        return builder.build();
    }

}
