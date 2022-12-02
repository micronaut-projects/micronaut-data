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

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CosmosResponse;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.ThroughputResponse;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.cosmos.config.StorageUpdatePolicy;
import io.micronaut.data.cosmos.config.ThroughputSettings;
import io.micronaut.data.cosmos.operations.CosmosDiagnosticsProcessor;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Initializes Cosmos Database and containers if needed and reads mapped entities into {@link CosmosEntity} instances.
 *
 * @author radovanradic
 * @since 3.9.0
 */
@Context
@Internal
@Requires(classes = CosmosClient.class)
final class CosmosDatabaseInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(CosmosDatabaseInitializer.class);

    /**
     * The initialize method will be called when dependencies are established so Cosmos Db can be initialized if needed.
     *
     * @param cosmosClient the Cosmos Db client
     * @param runtimeEntityRegistry the runtime entity registry
     * @param cosmosDiagnosticsProcessor the Cosmos diagnostics processor (can be null)
     * @param configuration the Cosmos Db configuration
     */
    @PostConstruct
    void initialize(CosmosClient cosmosClient,
                    RuntimeEntityRegistry runtimeEntityRegistry,
                    @Nullable
                    CosmosDiagnosticsProcessor cosmosDiagnosticsProcessor,
                    CosmosDatabaseConfiguration configuration) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Cosmos Db Initialization Start");
        }
        StorageUpdatePolicy storageUpdatePolicy = configuration.getUpdatePolicy();
        CosmosDatabase cosmosDatabase;
        if (StorageUpdatePolicy.NONE.equals(storageUpdatePolicy)) {
            cosmosDatabase = cosmosClient.getDatabase(configuration.getDatabaseName());
        } else {
            ThroughputProperties throughputProperties = configuration.getThroughput() != null ? configuration.getThroughput().createThroughputProperties() : null;
            cosmosDatabase = createOrUpdateDatabase(cosmosClient, cosmosDiagnosticsProcessor, configuration.getDatabaseName(), storageUpdatePolicy, throughputProperties);
        }
        initContainers(configuration, cosmosDatabase, runtimeEntityRegistry, cosmosDiagnosticsProcessor);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Cosmos Db Initialization Finish");
        }
    }

    /**
     * Based on {@link StorageUpdatePolicy} from the configuration, this method will create new database or update properties.
     * Currently, we only support configuring database throughput.
     *
     * @param cosmosClient the cosmos client
     * @param cosmosDiagnosticsProcessor the Cosmos diagnostics processor
     * @param databaseName the database name
     * @param storageUpdatePolicy the storage update policy, expected {@link StorageUpdatePolicy#CREATE_IF_NOT_EXISTS} or {@link StorageUpdatePolicy#UPDATE}
     * @param throughputProperties throughput properties from the configuration, can be null
     * @return CosmosDatabase instance for given database name
     */
    private CosmosDatabase createOrUpdateDatabase(CosmosClient cosmosClient, CosmosDiagnosticsProcessor cosmosDiagnosticsProcessor, String databaseName,
                                                  StorageUpdatePolicy storageUpdatePolicy, @Nullable ThroughputProperties throughputProperties) {
        CosmosDatabase cosmosDatabase;
        if (StorageUpdatePolicy.CREATE_IF_NOT_EXISTS.equals(storageUpdatePolicy)) {
            CosmosDatabaseResponse cosmosDatabaseResponse;
            try {
                if (throughputProperties != null) {
                    cosmosDatabaseResponse = cosmosClient.createDatabaseIfNotExists(databaseName, throughputProperties);
                } else {
                    cosmosDatabaseResponse = cosmosClient.createDatabaseIfNotExists(databaseName);
                }
            } catch (Exception e) {
                throw CosmosUtils.cosmosAccessException(cosmosDiagnosticsProcessor, CosmosDiagnosticsProcessor.CREATE_DATABASE_IF_NOT_EXISTS,
                    "Failed to create database", e);
            }
            processDiagnostics(CosmosDiagnosticsProcessor.CREATE_DATABASE_IF_NOT_EXISTS, cosmosDiagnosticsProcessor, cosmosDatabaseResponse);
            cosmosDatabase = cosmosClient.getDatabase(databaseName);
        } else if (StorageUpdatePolicy.UPDATE.equals(storageUpdatePolicy)) {
            cosmosDatabase = cosmosClient.getDatabase(databaseName);
            if (throughputProperties != null) {
                try {
                    ThroughputResponse throughputResponse = cosmosDatabase.replaceThroughput(throughputProperties);
                    processDiagnostics(CosmosDiagnosticsProcessor.REPLACE_DATABASE_THROUGHPUT, cosmosDiagnosticsProcessor, throughputResponse);
                } catch (Exception e) {
                    throw CosmosUtils.cosmosAccessException(cosmosDiagnosticsProcessor, CosmosDiagnosticsProcessor.REPLACE_DATABASE_THROUGHPUT,
                        "Failed to replace database throughput", e);
                }
            }
        } else {
            throw new ConfigurationException("Unexpected StorageUpdatePolicy value " + storageUpdatePolicy);
        }
        return cosmosDatabase;
    }

    private RuntimePersistentEntity<?>[] getPersistentEntities(CosmosDatabaseConfiguration configuration, RuntimeEntityRegistry runtimeEntityRegistry) {
        Collection<BeanIntrospection<Object>> introspections;
        if (CollectionUtils.isNotEmpty(configuration.getPackages())) {
            introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class, configuration.getPackages().toArray(new String[0]));
        } else {
            introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class);
        }
        return introspections.stream()
            // filter out inner / internal / abstract(MappedSuperClass) classes
            .filter(i -> !i.getBeanType().getName().contains("$"))
            .filter(i -> !java.lang.reflect.Modifier.isAbstract(i.getBeanType().getModifiers()))
            .map(e -> runtimeEntityRegistry.getEntity(e.getBeanType())).toArray(RuntimePersistentEntity<?>[]::new);
    }

    private void initContainers(CosmosDatabaseConfiguration configuration, CosmosDatabase cosmosDatabase, RuntimeEntityRegistry runtimeEntityRegistry,
                                CosmosDiagnosticsProcessor cosmosDiagnosticsProcessor) {
        RuntimePersistentEntity<?>[] entities = getPersistentEntities(configuration, runtimeEntityRegistry);
        Map<String, CosmosDatabaseConfiguration.CosmosContainerSettings> cosmosContainerSettings = CollectionUtils.isEmpty(configuration.getContainers()) ? Collections.emptyMap() :
            configuration.getContainers().stream().collect(Collectors.toMap(CosmosDatabaseConfiguration.CosmosContainerSettings::getContainerName, Function.identity()));
        for (RuntimePersistentEntity<?> persistentEntity : entities) {
            initContainer(cosmosContainerSettings, configuration.getUpdatePolicy(), persistentEntity, cosmosDatabase, cosmosDiagnosticsProcessor);
        }
    }

    private CosmosContainerResponse createContainer(CosmosDatabase cosmosDatabase, String containerName, String partitionKey, ThroughputProperties throughputProperties,
                                                    CosmosDiagnosticsProcessor cosmosDiagnosticsProcessor) {
        CosmosContainerProperties containerProperties = new CosmosContainerProperties(containerName, partitionKey);
        try {
            if (throughputProperties == null) {
                return cosmosDatabase.createContainerIfNotExists(containerProperties);
            } else {
                return cosmosDatabase.createContainerIfNotExists(containerProperties, throughputProperties);
            }
        } catch (Exception e) {
            throw CosmosUtils.cosmosAccessException(cosmosDiagnosticsProcessor, CosmosDiagnosticsProcessor.CREATE_CONTAINER_IF_NOT_EXISTS,
                "Failed to create container", e);
        }
    }

    private void initContainer(Map<String, CosmosDatabaseConfiguration.CosmosContainerSettings> cosmosContainerSettingsMap, StorageUpdatePolicy updatePolicy, RuntimePersistentEntity<?> entity, CosmosDatabase cosmosDatabase,
                               CosmosDiagnosticsProcessor cosmosDiagnosticsProcessor) {
        String containerName = entity.getPersistedName();
        CosmosDatabaseConfiguration.CosmosContainerSettings cosmosContainerSettings = cosmosContainerSettingsMap.get(containerName);
        CosmosEntity cosmosEntity = CosmosEntity.create(entity, cosmosContainerSettings);
        if (StorageUpdatePolicy.NONE.equals(updatePolicy)) {
            // Nothing to do, we don't create or  update containers
            return;
        }
        String partitionKey = cosmosEntity.getPartitionKey();
        ThroughputSettings throughputSettings = cosmosContainerSettings != null ? cosmosContainerSettings.getThroughput() : null;
        ThroughputProperties throughputProperties = throughputSettings != null ? throughputSettings.createThroughputProperties() : null;
        // TODO: Later implement indexing policy, time to live, unique key etc.
        if (StorageUpdatePolicy.CREATE_IF_NOT_EXISTS.equals(updatePolicy)) {
            CosmosContainerResponse containerResponse = createContainer(cosmosDatabase, containerName, partitionKey, throughputProperties, cosmosDiagnosticsProcessor);
            processDiagnostics(CosmosDiagnosticsProcessor.CREATE_CONTAINER_IF_NOT_EXISTS, cosmosDiagnosticsProcessor, containerResponse);
            return;
        }
        if (StorageUpdatePolicy.UPDATE.equals(updatePolicy)) {
            CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerName);
            if (throughputProperties != null) {
                try {
                    ThroughputResponse throughputResponse = cosmosContainer.replaceThroughput(throughputProperties);
                    processDiagnostics(CosmosDiagnosticsProcessor.REPLACE_CONTAINER_THROUGHPUT, cosmosDiagnosticsProcessor, throughputResponse);
                } catch (Exception e) {
                    throw CosmosUtils.cosmosAccessException(cosmosDiagnosticsProcessor, CosmosDiagnosticsProcessor.REPLACE_CONTAINER_THROUGHPUT,
                        "Failed to replace container throughput", e);
                }
            }
            CosmosContainerProperties containerProperties = cosmosContainer.read().getProperties();
            PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
            ArrayList<String> paths = new ArrayList<>();
            paths.add(partitionKey);
            partitionKeyDef.setPaths(paths);
            containerProperties.setPartitionKeyDefinition(partitionKeyDef);
            try {
                CosmosContainerResponse cosmosContainerResponse = cosmosContainer.replace(containerProperties);
                processDiagnostics(CosmosDiagnosticsProcessor.REPLACE_CONTAINER, cosmosDiagnosticsProcessor, cosmosContainerResponse);
            } catch (Exception e) {
                throw CosmosUtils.cosmosAccessException(cosmosDiagnosticsProcessor, CosmosDiagnosticsProcessor.REPLACE_CONTAINER,
                    "Failed to replace container properties", e);
            }
        }
    }

    /**
     * Processes diagnostics from the Cosmos response.
     *
     * @param operationName the operation name
     * @param cosmosDiagnosticsProcessor the Cosmos diagnostics processor
     * @param cosmosResponse the Cosmos response for various operations
     * @param <T> the type of resource
     */
    private <T> void processDiagnostics(String operationName, @Nullable CosmosDiagnosticsProcessor cosmosDiagnosticsProcessor, @Nullable CosmosResponse<T> cosmosResponse) {
        if (cosmosDiagnosticsProcessor == null || cosmosResponse == null) {
            return;
        }
        cosmosDiagnosticsProcessor.processDiagnostics(operationName, cosmosResponse.getDiagnostics(), cosmosResponse.getActivityId(), cosmosResponse.getRequestCharge());
    }
}
