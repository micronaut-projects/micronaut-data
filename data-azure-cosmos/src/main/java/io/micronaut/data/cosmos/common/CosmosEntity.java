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
package io.micronaut.data.cosmos.common;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.cosmos.annotation.ETag;
import io.micronaut.data.cosmos.annotation.PartitionKey;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model holding cosmos entity fields like container name, partition key, version field.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Internal
public final class CosmosEntity {

    private static final Logger LOG = LoggerFactory.getLogger(CosmosEntity.class);
    private static final Collection<DataType> IDENTITY_DATA_TYPES;
    private static final Map<RuntimePersistentEntity<?>, CosmosEntity> COSMOS_ENTITY_BY_PERSISTENT_ENTITY = new ConcurrentHashMap<>();

    static {
        IDENTITY_DATA_TYPES = Collections.unmodifiableList(Arrays.asList(DataType.SHORT, DataType.INTEGER, DataType.LONG, DataType.STRING, DataType.UUID));
    }

    private final String containerName;
    private final String partitionKey;
    private final String versionField;

    private CosmosEntity(@NonNull String containerName, @NonNull String partitionKey, @Nullable String versionField) {
        this.containerName = containerName;
        this.partitionKey = partitionKey;
        this.versionField = versionField;
    }

    /**
     * @return the Cosmos container name for this entity
     */
    public String getContainerName() {
        return containerName;
    }

    /**
     * @return the partition key for the Cosmos container/entity
     */
    public String getPartitionKey() {
        return partitionKey;
    }

    /**
     * @return the version field, if any defined on the entity using {@link ETag} annotation
     */
    public String getVersionField() {
        return versionField;
    }

    /**
     * Creates {@link CosmosEntity} from {@link RuntimePersistentEntity} and {@link io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration.CosmosContainerSettings}.
     *
     * @param runtimePersistentEntity the runtime persistent entity
     * @param cosmosContainerSettings the Cosmos container settings
     * @return the {@link CosmosEntity} holding mapped entity/container metadata
     */
    @NonNull
    public static CosmosEntity create(@NonNull RuntimePersistentEntity<?> runtimePersistentEntity, CosmosDatabaseConfiguration.CosmosContainerSettings cosmosContainerSettings) {
        return COSMOS_ENTITY_BY_PERSISTENT_ENTITY.computeIfAbsent(runtimePersistentEntity, e -> createCosmosEntity(runtimePersistentEntity, cosmosContainerSettings));
    }

    /**
     * Gets {@link CosmosEntity} that was initialized during app startup for given {@link RuntimePersistentEntity}.
     *
     * @param runtimePersistentEntity the runtime persistent entity
     * @return the {@link CosmosEntity} holding mapped entity/container metadata
     */
    @NonNull
    public static CosmosEntity get(@NonNull RuntimePersistentEntity<?> runtimePersistentEntity) {
        return COSMOS_ENTITY_BY_PERSISTENT_ENTITY.get(runtimePersistentEntity);
    }

    private static CosmosEntity createCosmosEntity(RuntimePersistentEntity<?> runtimePersistentEntity, CosmosDatabaseConfiguration.CosmosContainerSettings cosmosContainerSettings) {
        String containerName = runtimePersistentEntity.getPersistedName();
        String partitionKey = getPartitionKey(runtimePersistentEntity, cosmosContainerSettings);
        String versionField = null;
        BeanIntrospection<?> beanIntrospection = runtimePersistentEntity.getIntrospection();
        Collection<? extends BeanProperty<?, Object>> beanProperties = beanIntrospection.getBeanProperties();
        for (BeanProperty<?, Object> bp : beanProperties) {
            if (bp.hasStereotype(Transient.class)) {
                continue;
            }
            if (bp.hasStereotype(ETag.class)) {
                // If already found @ETag on one of previous field
                if (versionField != null) {
                    throw new IllegalStateException("Multiple @ETag annotations declared on " + runtimePersistentEntity.getPersistedName());
                }
                versionField = bp.getName();
            }
        }
        return new CosmosEntity(containerName, partitionKey, versionField);
    }

    private static String getPartitionKey(RuntimePersistentEntity<?> runtimePersistentEntity, CosmosDatabaseConfiguration.CosmosContainerSettings cosmosContainerSettings) {
        String partitionKey;
        if (cosmosContainerSettings != null && StringUtils.isNotEmpty(cosmosContainerSettings.getPartitionKeyPath())) {
            partitionKey = cosmosContainerSettings.getPartitionKeyPath();
        } else {
            partitionKey = findPartitionKey(runtimePersistentEntity);
        }
        if (StringUtils.isNotEmpty(partitionKey)) {
            if (!partitionKey.startsWith(Constants.PARTITION_KEY_SEPARATOR)) {
                partitionKey = Constants.PARTITION_KEY_SEPARATOR + partitionKey;
            }
            return partitionKey;
        }
        if (LOG.isInfoEnabled()) {
            LOG.info("Fallback to default partition key value since none is defined for entity {}", runtimePersistentEntity.getPersistedName());
        }
        return Constants.NO_PARTITION_KEY;
    }

    private static void validateIdentity(RuntimePersistentEntity<?> entity, PersistentProperty identity) {
        DataType dataType = identity.getDataType();
        if (!IDENTITY_DATA_TYPES.contains(dataType)) {
            throw new IllegalStateException("Entity " + entity.getPersistedName() + " has got unsupported identity type. Only supported types are: "
                + "Short, Integer, Long, String and UUID.");
        }
    }

    private static String findPartitionKey(RuntimePersistentEntity<?> runtimePersistentEntity) {
        String partitionKeyPath = "";
        List<PersistentProperty> properties = new ArrayList<>(runtimePersistentEntity.getPersistentProperties());
        PersistentProperty identity = runtimePersistentEntity.getIdentity();
        if (identity != null) {
            // check identity, we support only Short, Integer, Long, String and UUID
            // because we convert it to String when persisting and back when reading
            validateIdentity(runtimePersistentEntity, identity);
            properties.add(0, identity);
        }
        // Find partition key path
        for (PersistentProperty property : properties) {
            AnnotationValue<PartitionKey> partitionKeyAnnotationValue =
                property.getAnnotation(io.micronaut.data.cosmos.annotation.PartitionKey.class);
            if (partitionKeyAnnotationValue != null) {
                if (StringUtils.isNotEmpty(partitionKeyPath)) {
                    throw new IllegalStateException("Multiple @PartitionKey annotations declared on " + runtimePersistentEntity.getPersistedName()
                        + ". Azure Cosmos DB supports only one partition key.");
                }
                String partitionKeyValue = partitionKeyAnnotationValue.stringValue("value").orElse("");
                if (StringUtils.isNotEmpty(partitionKeyValue)) {
                    partitionKeyPath = partitionKeyValue;
                } else {
                    partitionKeyPath = property.getName();
                }
            }
        }
        return partitionKeyPath;
    }
}
