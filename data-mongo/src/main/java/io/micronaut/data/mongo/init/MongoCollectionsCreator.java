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
package io.micronaut.data.mongo.init;

import com.mongodb.client.MongoDatabase;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.mongo.database.MongoDatabaseFactory;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Mongo collections creator.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Context
@Internal
@Requires(property = "micronaut.data.mongo.create-collections", value = StringUtils.TRUE)
public final class MongoCollectionsCreator {
    private static final Logger LOG = LoggerFactory.getLogger(MongoCollectionsCreator.class);

    @PostConstruct
    void initialize(BeanLocator beanLocator,
                    RuntimeEntityRegistry runtimeEntityRegistry,
                    List<AbstractMongoConfiguration> mongoConfigurations) {

        for (AbstractMongoConfiguration mongoConfiguration : mongoConfigurations) {
            // TODO: different initializer per conf
            Collection<BeanIntrospection<Object>> introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class);
            PersistentEntity[] entities = introspections.stream()
                    // filter out inner / internal / abstract(MappedSuperClass) classes
                    .filter(i -> !i.getBeanType().getName().contains("$"))
                    .filter(i -> !java.lang.reflect.Modifier.isAbstract(i.getBeanType().getModifiers()))
                    .map(e -> runtimeEntityRegistry.getEntity(e.getBeanType())).toArray(PersistentEntity[]::new);

            MongoDatabaseFactory mongoDatabaseFactory;
            if (mongoConfiguration instanceof DefaultMongoConfiguration) {
                mongoDatabaseFactory = beanLocator.getBean(MongoDatabaseFactory.class);
            } else if (mongoConfiguration instanceof NamedMongoConfiguration) {
                Qualifier qualifier = Qualifiers.byName(((NamedMongoConfiguration) mongoConfiguration).getServerName());
                mongoDatabaseFactory = beanLocator.getBean(MongoDatabaseFactory.class, qualifier);
            } else {
                throw new IllegalStateException("Cannot get Mongo client for unrecognized configuration: " + mongoConfiguration);
            }

            Map<String, Set<String>> databaseCollections = new HashMap<>();
            for (PersistentEntity entity : entities) {
                MongoDatabase database = mongoDatabaseFactory.getDatabase(entity);
                Set<String> collections = databaseCollections.computeIfAbsent(database.getName(), s -> database.listCollectionNames().into(new HashSet<>()));
                String persistedName = entity.getPersistedName();
                if (collections.add(persistedName)) {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("Creating collection: {} in database: {}", persistedName, database.getName());
                    }
                    database.createCollection(persistedName);
                }
                for (PersistentProperty persistentProperty : entity.getPersistentProperties()) {
                    if (persistentProperty instanceof Association) {
                        Association association = (Association) persistentProperty;
                        Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
                        if (association.getKind() == Relation.Kind.MANY_TO_MANY || association.isForeignKey() && !inverseSide.isPresent()) {
                            Association owningAssociation = inverseSide.orElse(association);
                            NamingStrategy namingStrategy = association.getOwner().getNamingStrategy();
                            String joinCollectionName = namingStrategy.mappedName(owningAssociation);
                            if (collections.add(joinCollectionName)) {
                                if (LOG.isInfoEnabled()) {
                                    LOG.info("Creating collection: {} in database: {}", persistedName, database.getName());
                                }
                                database.createCollection(joinCollectionName);
                            }
                        }
                    }
                }

            }
        }

    }

}
