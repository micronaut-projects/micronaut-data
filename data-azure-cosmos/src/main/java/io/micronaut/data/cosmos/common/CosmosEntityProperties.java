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

import com.azure.cosmos.models.ThroughputProperties;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.cosmos.annotation.Container;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The model containing values read from {@link Container}.
 */
public final class CosmosEntityProperties {

    private static final Function<PersistentEntity, CosmosEntityProperties> COSMOS_CONTAINER_PROPERTIES_CREATOR =
        Memoizer.memoize(CosmosEntityProperties::getCosmosContainerProperties);

    private final String containerName;
    private final String partitionKeyPath;
    private final ThroughputProperties throughputProperties;

    /**
     * Creates an instance of {@link CosmosEntityProperties}.
     *
     * @param containerName the container name
     * @param partitionKeyPath the partition key path, may be blank
     * @param throughputProperties the throughput properties for the container, can be null and then not used on the container
     */
    public CosmosEntityProperties(String containerName, String partitionKeyPath, ThroughputProperties throughputProperties) {
        this.containerName = containerName;
        this.partitionKeyPath = partitionKeyPath;
        this.throughputProperties = throughputProperties;
    }

    /**
     * @return the container name
     */
    public String getContainerName() {
        return containerName;
    }

    /**
     * @return the partition key path for the container, can be empty
     */
    public String getPartitionKeyPath() {
        return partitionKeyPath;
    }

    /**
     * @return the container throughput properties, can be null
     */
    public ThroughputProperties getThroughputProperties() {
        return throughputProperties;
    }

    /**
     * Static Factory.
     *
     * @param entity the persistent entity
     * @return new CosmosContainerProperties
     */
    public static CosmosEntityProperties getInstance(PersistentEntity entity) {
        return COSMOS_CONTAINER_PROPERTIES_CREATOR.apply(entity);
    }

    private static CosmosEntityProperties getCosmosContainerProperties(PersistentEntity entity) {
        AnnotationValue<Container> containerData = entity.getAnnotation(Container.class);
        if (containerData != null) {
            String containerName = containerData.stringValue("name").orElse("");
            if (StringUtils.isEmpty(containerName)) {
                containerName = entity.getPersistedName();
            }
            String partitionKeyPath = findPartitionKey(entity);
            if (StringUtils.isEmpty(partitionKeyPath)) {
                // If not defined on any field, use from the container (also may be not defined on container level)
                partitionKeyPath = containerData.stringValue("partitionKeyPath").orElse("");
            }
            int throughputRequestUnits = containerData.intValue("throughputRequestUnits").orElse(0);
            ThroughputProperties throughputProperties = null;
            if (throughputRequestUnits > 0) {
                boolean throughputAutoScale = containerData.booleanValue("throughputAutoScale").orElse(false);
                if (throughputAutoScale) {
                    throughputProperties = ThroughputProperties.createAutoscaledThroughput(throughputRequestUnits);
                } else {
                    throughputProperties = ThroughputProperties.createManualThroughput(throughputRequestUnits);
                }
            }
            PersistentProperty versionProperty = entity.getVersion();
            if (versionProperty != null && !DataType.STRING.equals(versionProperty.getDataType())) {
                throw new IllegalStateException("Version field in Cosmos Db must be string");
            }
            return new CosmosEntityProperties(containerName, partitionKeyPath, throughputProperties);
        }
        return null;
    }

    private static String findPartitionKey(PersistentEntity entity) {
        String partitionKeyPath = "";
        List<PersistentProperty> properties = new ArrayList<>(entity.getPersistentProperties());
        PersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            properties.add(0, identity);
        }
        // Find partition key path
        for (PersistentProperty property : properties) {
            AnnotationValue<io.micronaut.data.cosmos.annotation.PartitionKey> partitionKeyAnnotationValue =
                property.getAnnotation(io.micronaut.data.cosmos.annotation.PartitionKey.class);
            if (partitionKeyAnnotationValue != null) {
                if (StringUtils.isNotEmpty(partitionKeyPath)) {
                    throw new IllegalStateException("Multiple @PartitionKey annotations declared on " + entity.getName()
                        + ". Azure Cosmos DB supports only one partition key.");
                }
                String partitionKeyValue = partitionKeyAnnotationValue.stringValue("value").orElse("");
                if (StringUtils.isNotEmpty(partitionKeyValue)) {
                    partitionKeyPath = partitionKeyValue;
                } else {
                    partitionKeyPath = property.getPersistedName();
                }
            }
        }
        return partitionKeyPath;
    }
}
