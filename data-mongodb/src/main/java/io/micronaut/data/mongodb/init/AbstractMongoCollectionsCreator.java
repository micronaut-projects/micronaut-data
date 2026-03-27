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

import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.mongodb.annotation.MongoIndexDirection;
import io.micronaut.data.mongodb.common.MongoEntityIndexes;
import io.micronaut.data.mongodb.conf.MongoDataConfiguration;
import io.micronaut.data.mongodb.operations.MongoCollectionNameProvider;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * MongoDB's collections creator.
 *
 * @param <Dtbs> The MongoDB database type
 * @author Denis Stepanov
 * @since 3.3
 */
@Context
@Internal
public class AbstractMongoCollectionsCreator<Dtbs> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoCollectionsCreator.class);

    /**
     * Get MongoDB database factory.
     *
     * @param mongoFactoryClass  The factory class
     * @param beanLocator        The bean locator
     * @param mongoConfiguration The configurtion
     * @param <M>                The mongo factory type
     * @return THe factory instance.
     */
    protected <M> M getMongoFactory(Class<M> mongoFactoryClass, BeanLocator beanLocator, AbstractMongoConfiguration mongoConfiguration) {
        if (mongoConfiguration instanceof DefaultMongoConfiguration) {
            return beanLocator.getBean(mongoFactoryClass);
        } else if (mongoConfiguration instanceof NamedMongoConfiguration namedMongoConfiguration) {
            Qualifier<M> qualifier = Qualifiers.byName(namedMongoConfiguration.getServerName());
            return beanLocator.getBean(mongoFactoryClass, qualifier);
        } else {
            throw new IllegalStateException("Cannot get MongoDB client for unrecognized configuration: " + mongoConfiguration);
        }
    }

    /**
     * Initialize the collections.
     *
     * @param runtimeEntityRegistry      The entity registry
     * @param environment                The environment
     * @param mongoConfigurations        The configuration
     * @param mongoDataConfiguration     The Mongo data configuration
     * @param databaseOperationsProvider The database provider
     * @param mongoCollectionNameProvider The Mongo collection name provider
     */
    protected void initialize(RuntimeEntityRegistry runtimeEntityRegistry,
                              Environment environment,
                              List<AbstractMongoConfiguration> mongoConfigurations,
                              MongoDataConfiguration mongoDataConfiguration,
                              DatabaseOperationsProvider<Dtbs> databaseOperationsProvider,
                              MongoCollectionNameProvider mongoCollectionNameProvider) {

        boolean createCollections = mongoDataConfiguration.isCreateCollections();
        boolean createIndexes = mongoDataConfiguration.isCreateIndexes();
        if (!createCollections && !createIndexes) {
            return;
        }

        for (AbstractMongoConfiguration mongoConfiguration : mongoConfigurations) {
            List<String> packageNames = environment.getProperty("mongodb.package-names", List.class).orElseGet(List::of);
            List<Class<?>> entityTypes;
            if (!packageNames.isEmpty()) {
                entityTypes = environment.scan(MappedEntity.class, packageNames.toArray(new String[0])).toList();
            } else {
                entityTypes = environment.scan(MappedEntity.class).toList();
            }
            PersistentEntity[] entities = entityTypes.stream()
                    .filter(type -> !type.getName().contains("$"))
                    .filter(type -> !type.isSynthetic())
                    .map(runtimeEntityRegistry::getEntity)
                    .toArray(PersistentEntity[]::new);

            DatabaseOperations<Dtbs> databaseOperations = databaseOperationsProvider.get(mongoConfiguration);

            for (PersistentEntity entity : entities) {
                Dtbs database = databaseOperations.find(entity);
                Set<String> collections = databaseOperations.listCollectionNames(database);
                String persistedName = mongoCollectionNameProvider.provide(entity);
                boolean collectionExists = collections.contains(persistedName);
                if (!collectionExists && createCollections) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("Creating collection: {} in database: {}", persistedName, databaseOperations.getDatabaseName(database));
                    }
                    databaseOperations.createCollection(database, persistedName);
                    collections.add(persistedName);
                }
                if ((collectionExists || createCollections) && createIndexes) {
                    createIndexes(databaseOperations, database, entity, persistedName);
                }
                if (createCollections) {
                    createJoinCollections(databaseOperations, database, collections, entity, persistedName);
                }
            }
        }
    }

    private void createJoinCollections(DatabaseOperations<Dtbs> databaseOperations,
                                       Dtbs database,
                                       Set<String> collections,
                                       PersistentEntity entity,
                                       String persistedName) {
        for (PersistentProperty persistentProperty : entity.getPersistentProperties()) {
            if (persistentProperty instanceof Association association) {
                Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
                if (association.getKind() == Relation.Kind.MANY_TO_MANY || (association.isForeignKey() && inverseSide.isEmpty())) {
                    Association owningAssociation = inverseSide.orElse(association);
                    NamingStrategy namingStrategy = association.getOwner().getNamingStrategy();
                    String joinCollectionName = namingStrategy.mappedName(owningAssociation);
                    if (collections.add(joinCollectionName)) {
                        if (LOG.isInfoEnabled()) {
                            LOG.info("Creating collection: {} in database: {}", persistedName, databaseOperations.getDatabaseName(database));
                        }
                        databaseOperations.createCollection(database, joinCollectionName);
                    }
                }
            }
        }
    }

    private void createIndexes(DatabaseOperations<Dtbs> databaseOperations,
                               Dtbs database,
                               PersistentEntity entity,
                               String collectionName) {
        List<MongoResolvedIndex> desiredIndexes = resolveIndexes(entity);
        if (desiredIndexes.isEmpty()) {
            return;
        }
        List<MongoResolvedIndex> existingIndexes = databaseOperations.listIndexes(database, collectionName);
        for (MongoResolvedIndex desiredIndex : desiredIndexes) {
            MongoResolvedIndex existingIndex = findMatchingIndex(existingIndexes, desiredIndex);
            if (existingIndex == null) {
                databaseOperations.createIndex(database, collectionName, desiredIndex);
                continue;
            }
            if (!existingIndex.matchesManagedOptions(desiredIndex)) {
                throw new IllegalStateException("Conflicting existing MongoDB index for entity [" + entity.getName() + "] and collection [" + collectionName + "]: desired " + desiredIndex.describe() + ", existing " + existingIndex.describe());
            }
            if (desiredIndex.name() != null && existingIndex.name() != null && !desiredIndex.name().equals(existingIndex.name())) {
                throw new IllegalStateException("Conflicting existing MongoDB index name for entity [" + entity.getName() + "] and collection [" + collectionName + "]: desired " + desiredIndex.describe() + ", existing " + existingIndex.describe());
            }
        }
    }

    @Nullable
    private MongoResolvedIndex findMatchingIndex(List<MongoResolvedIndex> existingIndexes, MongoResolvedIndex desiredIndex) {
        for (MongoResolvedIndex existingIndex : existingIndexes) {
            if (existingIndex.hasSameKey(desiredIndex)) {
                return existingIndex;
            }
        }
        return null;
    }

    private List<MongoResolvedIndex> resolveIndexes(PersistentEntity entity) {
        RuntimePersistentEntity<?> runtimePersistentEntity = (RuntimePersistentEntity<?>) entity;
        List<MongoResolvedIndex> indexes = new ArrayList<>();
        for (MongoEntityIndexes.ResolvedIndex index : MongoEntityIndexes.create(runtimePersistentEntity).getIndexes()) {
            List<MongoResolvedIndexField> fields = index.fields().stream()
                    .map(field -> new MongoResolvedIndexField(field.path(), field.order(), field.weight(), field.kind(), field.min(), field.max()))
                    .toList();
            indexes.add(new MongoResolvedIndex(index.name(), fields, index.unique(), index.sparse(), index.expireAfterSeconds(), index.partialFilterExpression(), index.collation(), index.bits(), index.min(), index.max(), index.wildcardProjection()));
        }
        return indexes;
    }

    private int toOrder(MongoIndexDirection direction) {
        return direction == MongoIndexDirection.DESC ? -1 : 1;
    }

    /**
     * The MongoDB database operations provider.
     *
     * @param <Dtbs> The database type
     */
    interface DatabaseOperationsProvider<Dtbs> {

        /**
         * Gets {@link DatabaseOperations} for given configuration.
         *
         * @param mongoConfiguration The mongo configuration
         * @return The database operations
         */
        DatabaseOperations<Dtbs> get(AbstractMongoConfiguration mongoConfiguration);

    }

    /**
     * The MongoDB database operations.
     *
     * @param <Dtbs> The database type
     */
    interface DatabaseOperations<Dtbs> {

        /**
         * Get database name.
         *
         * @param database The database
         * @return The name
         */
        String getDatabaseName(Dtbs database);

        /**
         * Find database that should be used for the given persistent entity.
         *
         * @param persistentEntity The persistent entity
         * @return The database
         */
        Dtbs find(PersistentEntity persistentEntity);

        /**
         * List collections in the given database.
         *
         * @param database The database
         * @return The collections
         */
        Set<String> listCollectionNames(Dtbs database);

        /**
         * Create a collection in the given database.
         *
         * @param database   The database
         * @param collection The collection
         */
        void createCollection(Dtbs database, String collection);

        /**
         * List indexes for the given collection.
         *
         * @param database The database
         * @param collection The collection name
         * @return The indexes
         */
        List<MongoResolvedIndex> listIndexes(Dtbs database, String collection);

        /**
         * Create an index for the given collection.
         *
         * @param database The database
         * @param collection The collection name
         * @param index The index
         */
        void createIndex(Dtbs database, String collection, MongoResolvedIndex index);

    }

    @Internal
    record MongoResolvedIndexField(String path, @Nullable Integer order, @Nullable Integer weight, @Nullable String kind, @Nullable Double min, @Nullable Double max) {
    }

    @Internal
    record MongoResolvedIndex(@Nullable String name,
                              List<MongoResolvedIndexField> fields,
                              boolean unique,
                              boolean sparse,
                              @Nullable Integer expireAfterSeconds,
                              @Nullable String partialFilterExpression,
                              @Nullable String collation,
                              @Nullable Integer bits,
                              @Nullable Double min,
                              @Nullable Double max,
                              @Nullable String wildcardProjection) {

        boolean hasSameKey(MongoResolvedIndex other) {
            return fields.equals(other.fields);
        }

        boolean matchesManagedOptions(MongoResolvedIndex other) {
            return unique == other.unique
                    && sparse == other.sparse
                    && Objects.equals(expireAfterSeconds, other.expireAfterSeconds)
                    && Objects.equals(partialFilterExpression, other.partialFilterExpression)
                    && Objects.equals(collation, other.collation)
                    && Objects.equals(bits, other.bits)
                    && Objects.equals(min, other.min)
                    && Objects.equals(max, other.max)
                    && Objects.equals(wildcardProjection, other.wildcardProjection);
        }

        Document keysDocument() {
            Document document = new Document();
            for (MongoResolvedIndexField field : fields) {
                if (field.order() != null) {
                    document.append(field.path(), field.order());
                } else if (field.kind() != null) {
                    document.append(field.path(), field.kind());
                } else {
                    document.append(field.path(), "text");
                }
            }
            return document;
        }

        String describe() {
            return "MongoResolvedIndex{name=" + name + ", fields=" + fields + ", unique=" + unique + ", sparse=" + sparse + ", expireAfterSeconds=" + expireAfterSeconds + ", partialFilterExpression=" + partialFilterExpression + '}';
        }
    }
}
