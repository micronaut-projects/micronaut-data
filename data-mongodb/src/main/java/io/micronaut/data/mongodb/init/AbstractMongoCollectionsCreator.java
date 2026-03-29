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

import com.mongodb.MongoException;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteError;
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
import io.micronaut.data.mongodb.annotation.MongoClusteredIndex;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;

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
    private static final int INDEX_OPTIONS_CONFLICT_CODE = 85;
    private static final int INDEX_KEY_SPECS_CONFLICT_CODE = 86;
    private static final String INDEX_OPTIONS_CONFLICT_CODE_NAME = "IndexOptionsConflict";
    private static final String INDEX_KEY_SPECS_CONFLICT_CODE_NAME = "IndexKeySpecsConflict";

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
                MongoResolvedCollectionOptions desiredCollectionOptions = resolveCollectionOptions(entity);
                boolean collectionExists = collections.contains(persistedName);
                if (collectionExists && desiredCollectionOptions != null) {
                    MongoResolvedCollectionOptions existingCollectionOptions = databaseOperations.getCollectionOptions(database, persistedName);
                    if (!desiredCollectionOptions.matches(existingCollectionOptions)) {
                        throw new IllegalStateException("Conflicting existing MongoDB collection options for entity [" + entity.getName() + "] and collection [" + persistedName + "]: desired " + desiredCollectionOptions.describe() + ", existing " + (existingCollectionOptions == null ? "null" : existingCollectionOptions.describe()));
                    }
                }
                if (!collectionExists && createCollections) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("Creating collection: {} in database: {}", persistedName, databaseOperations.getDatabaseName(database));
                    }
                    databaseOperations.createCollection(database, persistedName, desiredCollectionOptions);
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
                        databaseOperations.createCollection(database, joinCollectionName, null);
                    }
                }
            }
        }
    }

    @Nullable
    private MongoResolvedCollectionOptions resolveCollectionOptions(PersistentEntity entity) {
        RuntimePersistentEntity<?> runtimePersistentEntity = (RuntimePersistentEntity<?>) entity;
        var annotation = runtimePersistentEntity.getAnnotationMetadata().getAnnotation(MongoClusteredIndex.class);
        if (annotation == null) {
            return null;
        }
        boolean unique = annotation.booleanValue("unique").orElse(true);
        if (!unique) {
            throw new IllegalStateException("Mongo clustered index for entity [" + entity.getName() + "] must be unique=true");
        }
        Integer expireAfterSeconds = annotation.intValue("expireAfterSeconds").isPresent() && annotation.intValue("expireAfterSeconds").getAsInt() >= 0
                ? annotation.intValue("expireAfterSeconds").getAsInt() : null;
        if (expireAfterSeconds != null) {
            PersistentProperty identity = entity.getIdentity();
            if (identity == null) {
                throw new IllegalStateException("Mongo clustered TTL collection for entity [" + entity.getName() + "] requires an identity property");
            }
            String idType = identity.getTypeName();
            boolean supportedTtlIdType = idType.equals(Date.class.getName())
                    || idType.equals(Instant.class.getName())
                    || idType.equals(LocalDateTime.class.getName())
                    || idType.equals(OffsetDateTime.class.getName());
            if (!supportedTtlIdType) {
                throw new IllegalStateException("Mongo clustered TTL collection for entity [" + entity.getName() + "] requires a date/time identity type, but found [" + idType + "]");
            }
        }
        return new MongoResolvedCollectionOptions(
                annotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                unique,
                expireAfterSeconds
        );
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
                try {
                    databaseOperations.createIndex(database, collectionName, desiredIndex);
                } catch (RuntimeException e) {
                    throw mapCreateIndexConflict(e, entity, collectionName, desiredIndex);
                }
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
            indexes.add(new MongoResolvedIndex(
                    index.name(),
                    fields,
                    index.unique(),
                    index.sparse(),
                    index.hidden(),
                    index.expireAfterSeconds(),
                    normalizeJsonString(index.partialFilterExpression()),
                    normalizeJsonString(index.collation()),
                    index.bits(),
                    index.min(),
                    index.max(),
                    index.defaultLanguage(),
                    index.languageOverride(),
                    index.textIndexVersion(),
                    index.sphereVersion(),
                    normalizeJsonString(index.wildcardProjection()),
                    normalizeJsonString(index.storageEngine()),
                    index.comment(),
                    index.commitQuorum()
            ));
        }
        return indexes;
    }

    static @Nullable String normalizeJsonValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Document document) {
            return document.toJson();
        }
        if (value instanceof org.bson.BsonDocument bsonDocument) {
            return bsonDocument.toJson();
        }
        if (value instanceof String stringValue) {
            return normalizeJsonString(stringValue);
        }
        return value.toString();
    }

    static @Nullable String normalizeJsonString(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Document.parse(trimmed).toJson();
        } catch (RuntimeException ignored) {
            return trimmed;
        }
    }

    static @Nullable Integer toInteger(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    static @Nullable Double toDouble(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private RuntimeException mapCreateIndexConflict(RuntimeException e,
                                                    PersistentEntity entity,
                                                    String collectionName,
                                                    MongoResolvedIndex desiredIndex) {
        MongoIndexConflict mongoIndexConflict = findMongoIndexConflict(e);
        if (mongoIndexConflict == null) {
            return e;
        }
        return new IllegalStateException("Conflicting existing MongoDB index while creating desired index for entity ["
                + entity.getName()
                + "] and collection ["
                + collectionName
                + "]: desired "
                + desiredIndex.describe()
                + ", mongoErrorCodeName="
                + mongoIndexConflict.errorCodeName()
                + ", mongoErrorCode="
                + mongoIndexConflict.errorCode()
                + ", mongoMessage="
                + String.valueOf(mongoIndexConflict.message()), e);
    }

    private @Nullable MongoIndexConflict findMongoIndexConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MongoCommandException mongoCommandException) {
                int errorCode = mongoCommandException.getErrorCode();
                String errorCodeName = mongoCommandException.getErrorCodeName();
                if (isIndexConflict(errorCode, errorCodeName)) {
                    return new MongoIndexConflict(errorCode, errorCodeName, mongoCommandException.getMessage());
                }
            }
            if (current instanceof MongoWriteException mongoWriteException) {
                WriteError writeError = mongoWriteException.getError();
                int errorCode = writeError.getCode();
                if (isIndexConflict(errorCode, null)) {
                    return new MongoIndexConflict(errorCode, null, mongoWriteException.getMessage());
                }
            }
            if (current instanceof MongoException mongoException) {
                int errorCode = mongoException.getCode();
                if (isIndexConflict(errorCode, null)) {
                    return new MongoIndexConflict(errorCode, null, mongoException.getMessage());
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean isIndexConflict(int errorCode,
                                    @Nullable String errorCodeName) {
        return errorCode == INDEX_OPTIONS_CONFLICT_CODE
                || errorCode == INDEX_KEY_SPECS_CONFLICT_CODE
                || INDEX_OPTIONS_CONFLICT_CODE_NAME.equals(errorCodeName)
                || INDEX_KEY_SPECS_CONFLICT_CODE_NAME.equals(errorCodeName);
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
         * Create a collection in the given database with options.
         *
         * @param database   The database
         * @param collection The collection
         * @param options The resolved collection options
         */
        void createCollection(Dtbs database, String collection, @Nullable MongoResolvedCollectionOptions options);

        /**
         * Read collection options for an existing collection.
         *
         * @param database The database
         * @param collection The collection
         * @return The collection options if clustered, or {@code null}
         */
        @Nullable
        MongoResolvedCollectionOptions getCollectionOptions(Dtbs database, String collection);

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
    record MongoResolvedCollectionOptions(@Nullable String clusteredIndexName,
                                          boolean clusteredIndexUnique,
                                          @Nullable Integer expireAfterSeconds) {

        boolean matches(@Nullable MongoResolvedCollectionOptions other) {
            return other != null
                    && clusteredIndexUnique == other.clusteredIndexUnique
                    && Objects.equals(expireAfterSeconds, other.expireAfterSeconds)
                    && (clusteredIndexName == null || other.clusteredIndexName == null || Objects.equals(clusteredIndexName, other.clusteredIndexName));
        }

        String describe() {
            return "MongoResolvedCollectionOptions{clusteredIndexName=" + clusteredIndexName
                    + ", clusteredIndexUnique=" + clusteredIndexUnique
                    + ", expireAfterSeconds=" + expireAfterSeconds
                    + '}';
        }
    }

    @Internal
    record MongoResolvedIndexField(String path, @Nullable Integer order, @Nullable Integer weight, @Nullable String kind, @Nullable Double min, @Nullable Double max) {
    }

    private record MongoIndexConflict(int errorCode,
                                      @Nullable String errorCodeName,
                                      @Nullable String message) {
    }

    @Internal
    record MongoResolvedIndex(@Nullable String name,
                              List<MongoResolvedIndexField> fields,
                              boolean unique,
                              boolean sparse,
                              boolean hidden,
                              @Nullable Integer expireAfterSeconds,
                              @Nullable String partialFilterExpression,
                              @Nullable String collation,
                              @Nullable Integer bits,
                              @Nullable Double min,
                              @Nullable Double max,
                              @Nullable String defaultLanguage,
                              @Nullable String languageOverride,
                              @Nullable Integer textIndexVersion,
                              @Nullable Integer sphereVersion,
                              @Nullable String wildcardProjection,
                              @Nullable String storageEngine,
                              @Nullable String comment,
                              @Nullable String commitQuorum) {

        boolean hasSameKey(MongoResolvedIndex other) {
            return fields.equals(other.fields);
        }

        boolean matchesManagedOptions(MongoResolvedIndex other) {
            return unique == other.unique
                    && sparse == other.sparse
                    && hidden == other.hidden
                    && Objects.equals(expireAfterSeconds, other.expireAfterSeconds)
                    && Objects.equals(partialFilterExpression, other.partialFilterExpression)
                    && collationMatches(collation, other.collation)
                    && optionMatches(other.bits, bits)
                    && optionMatches(other.min, min)
                    && optionMatches(other.max, max)
                    && optionMatches(other.defaultLanguage, defaultLanguage)
                    && optionMatches(other.languageOverride, languageOverride)
                    && optionMatches(other.textIndexVersion, textIndexVersion)
                    && optionMatches(other.sphereVersion, sphereVersion)
                    && Objects.equals(wildcardProjection, other.wildcardProjection)
                    && Objects.equals(storageEngine, other.storageEngine);
        }

        private static <T> boolean optionMatches(@Nullable T desired,
                                                 @Nullable T existing) {
            return desired == null || Objects.equals(desired, existing);
        }

        private static boolean collationMatches(@Nullable String existingCollation,
                                                @Nullable String desiredCollation) {
            if (desiredCollation == null) {
                return existingCollation == null;
            }
            if (existingCollation == null) {
                return false;
            }
            try {
                Document desired = Document.parse(desiredCollation);
                Document existing = Document.parse(existingCollation);
                for (String key : desired.keySet()) {
                    if (!Objects.equals(existing.get(key), desired.get(key))) {
                        return false;
                    }
                }
                return true;
            } catch (RuntimeException ignored) {
                return Objects.equals(existingCollation, desiredCollation);
            }
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
            return "MongoResolvedIndex{name=" + name
                    + ", fields=" + fields
                    + ", unique=" + unique
                    + ", sparse=" + sparse
                    + ", hidden=" + hidden
                    + ", expireAfterSeconds=" + expireAfterSeconds
                    + ", partialFilterExpression=" + partialFilterExpression
                    + ", collation=" + collation
                    + ", bits=" + bits
                    + ", min=" + min
                    + ", max=" + max
                    + ", defaultLanguage=" + defaultLanguage
                    + ", languageOverride=" + languageOverride
                    + ", textIndexVersion=" + textIndexVersion
                    + ", sphereVersion=" + sphereVersion
                    + ", wildcardProjection=" + wildcardProjection
                    + ", storageEngine=" + storageEngine
                    + ", comment=" + comment
                    + ", commitQuorum=" + commitQuorum
                    + '}';
        }
    }
}
