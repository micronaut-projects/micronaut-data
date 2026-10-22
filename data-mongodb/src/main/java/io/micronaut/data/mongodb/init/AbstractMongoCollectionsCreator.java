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
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.micronaut.data.annotation.search.Language;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchWeight;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.search.TextSearchSupport;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jspecify.annotations.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.mongodb.operations.MongoCollectionNameProvider;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
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
     * @param mongoConfigurations        The configuration
     * @param databaseOperationsProvider The database provider
     * @param mongoCollectionNameProvider The Mongo collection name provider
     */
    protected void initialize(RuntimeEntityRegistry runtimeEntityRegistry,
                              List<AbstractMongoConfiguration> mongoConfigurations,
                              DatabaseOperationsProvider<Dtbs> databaseOperationsProvider,
                              MongoCollectionNameProvider mongoCollectionNameProvider) {

        for (AbstractMongoConfiguration mongoConfiguration : mongoConfigurations) {
            // TODO: different initializer per conf
            Collection<BeanIntrospection<Object>> introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class);
            PersistentEntity[] entities = introspections.stream()
                    // filter out inner / internal / abstract(MappedSuperClass) classes
                    .filter(i -> !i.getBeanType().getName().contains("$"))
                    .filter(i -> !Modifier.isAbstract(i.getBeanType().getModifiers()))
                    .map(e -> runtimeEntityRegistry.getEntity(e.getBeanType())).toArray(PersistentEntity[]::new);

            DatabaseOperations<Dtbs> databaseOperations = databaseOperationsProvider.get(mongoConfiguration);

            for (PersistentEntity entity : entities) {
                Dtbs database = databaseOperations.find(entity);
                Set<String> collections = databaseOperations.listCollectionNames(database);
                String persistedName = mongoCollectionNameProvider.provide(entity);
                if (collections.add(persistedName)) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("Creating collection: {} in database: {}", persistedName, databaseOperations.getDatabaseName(database));
                    }
                    databaseOperations.createCollection(database, persistedName);
                }
                TextIndexDefinition textIndex = textIndexDefinition(entity, persistedName);
                if (textIndex != null && !databaseOperations.listIndexNames(database, persistedName).contains(textIndex.name())) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("Creating text index: {} on collection: {} in database: {}", textIndex.name(), persistedName, databaseOperations.getDatabaseName(database));
                    }
                    databaseOperations.createTextIndex(database, persistedName, textIndex);
                }
                for (PersistentProperty persistentProperty : entity.getPersistentProperties()) {
                    if (persistentProperty instanceof Association association) {
                        Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
                        if (association.getKind() == Relation.Kind.MANY_TO_MANY || association.isForeignKey() && !inverseSide.isPresent()) {
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
        }

    }

    /**
     * Describes the text index of a {@link Searchable} entity.
     *
     * @param entity The entity
     * @param collection The collection name
     * @return The definition, or {@code null} when the entity is not searchable
     */
    @Nullable
    static TextIndexDefinition textIndexDefinition(PersistentEntity entity, String collection) {
        if (!entity.getAnnotationMetadata().hasAnnotation(Searchable.class)) {
            return null;
        }
        List<PersistentProperty> fields = TextSearchSupport.searchFields(entity);
        if (fields.isEmpty()) {
            return null;
        }
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (PersistentProperty field : fields) {
            int weight = field.getAnnotationMetadata().enumValue(SearchField.class, SearchField.WEIGHT, SearchWeight.class)
                .map(SearchWeight::numericWeight)
                .orElse(1);
            weights.put(persistedFieldName(field), weight);
        }
        String languageOverride = entity.getPersistentProperties().stream()
            .filter(p -> p.getAnnotationMetadata().hasAnnotation(Language.class))
            .map(AbstractMongoCollectionsCreator::persistedFieldName)
            .findFirst()
            .orElse(null);
        String name = entity.getAnnotationMetadata().stringValue(Searchable.class, "name")
            .filter(n -> !n.isEmpty())
            .orElse(collection + "_text");
        return new TextIndexDefinition(name, weights, TextSearchSupport.resolveLanguage(entity), languageOverride);
    }

    private static String persistedFieldName(PersistentProperty property) {
        return property.getAnnotationMetadata().stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElseGet(property::getName);
    }

    /**
     * A MongoDB text index over the search fields of an entity.
     *
     * @param name The index name
     * @param weights The indexed field names with their weights
     * @param defaultLanguage The default language
     * @param languageOverride The field holding the per-document language, or {@code null}
     */
    public record TextIndexDefinition(String name,
                                      Map<String, Integer> weights,
                                      String defaultLanguage,
                                      @Nullable String languageOverride) {

        /**
         * @return The index keys
         */
        public Bson keys() {
            return Indexes.compoundIndex(weights.keySet().stream().map(Indexes::text).toList());
        }

        /**
         * @return The index options
         */
        public IndexOptions options() {
            IndexOptions options = new IndexOptions()
                .name(name)
                .weights(new Document(new LinkedHashMap<>(weights)))
                .defaultLanguage(defaultLanguage);
            if (languageOverride != null) {
                options.languageOverride(languageOverride);
            }
            return options;
        }
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
         * List the index names of a collection.
         *
         * @param database The database
         * @param collection The collection
         * @return The index names
         */
        Set<String> listIndexNames(Dtbs database, String collection);

        /**
         * Create the text index of a searchable entity.
         *
         * @param database The database
         * @param collection The collection
         * @param definition The index definition
         */
        void createTextIndex(Dtbs database, String collection, TextIndexDefinition definition);
    }

}
