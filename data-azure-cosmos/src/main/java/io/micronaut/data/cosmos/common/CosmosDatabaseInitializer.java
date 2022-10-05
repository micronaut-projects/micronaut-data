package io.micronaut.data.cosmos.common;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.cosmos.annotation.PartitionKey;
import io.micronaut.data.cosmos.config.CosmosContainerSettings;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.cosmos.config.StorageUpdatePolicy;
import io.micronaut.data.cosmos.config.ThroughputSettings;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Initializes Cosmos Database and Containers.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 */
@Context
@Internal
@Requires(classes = CosmosClient.class)
@Requires(property = CosmosDatabaseConfiguration.UPDATE_POLICY, notEquals = "NONE")
public class CosmosDatabaseInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(CosmosDatabaseInitializer.class);

    @PostConstruct
    public void initialize(CosmosClient cosmosClient,
                           RuntimeEntityRegistry runtimeEntityRegistry,
                           CosmosDatabaseConfiguration configuration) {
        LOG.debug("Cosmos Db Initialization Start");
        ThroughputProperties throughputProperties = createThroughputProperties(configuration.getThroughput());
        CosmosDatabase cosmosDatabase;
        if (configuration.getUpdatePolicy().equals(StorageUpdatePolicy.CREATE_IF_NOT_EXISTS)) {
            if (throughputProperties != null) {
                cosmosClient.createDatabaseIfNotExists(configuration.getDatabaseName(), throughputProperties);
            } else {
                cosmosClient.createDatabaseIfNotExists(configuration.getDatabaseName());
            }
            cosmosDatabase = cosmosClient.getDatabase(configuration.getDatabaseName());
        } else if (configuration.getUpdatePolicy().equals(StorageUpdatePolicy.UPDATE)) {
            cosmosDatabase = cosmosClient.getDatabase(configuration.getDatabaseName());
            if (throughputProperties != null) {
                cosmosDatabase.replaceThroughput(throughputProperties);
            }
        } else {
            throw new ConfigurationException("Unexpected StorageUpdatePolicy value " + configuration.getUpdatePolicy());
        }
        initContainers(configuration, cosmosDatabase, runtimeEntityRegistry);
        LOG.debug("Cosmos Db Initialization Finish");
    }

    private ThroughputProperties createThroughputProperties(ThroughputSettings throughputSettings) {
        if (throughputSettings != null && throughputSettings.getRequestUnits() != null) {
            if (throughputSettings.isAutoScale()) {
                return ThroughputProperties.createAutoscaledThroughput(throughputSettings.getRequestUnits());
            } else {
                return ThroughputProperties.createManualThroughput(throughputSettings.getRequestUnits());
            }
        }
        return null;
    }

    private void initContainers(CosmosDatabaseConfiguration configuration, CosmosDatabase cosmosDatabase, RuntimeEntityRegistry runtimeEntityRegistry) {
        Collection<BeanIntrospection<Object>> introspections;
        if (CollectionUtils.isNotEmpty(configuration.getPackages())) {
            introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class, configuration.getPackages().toArray(new String[0]));
        } else {
            introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class);
        }
        PersistentEntity[] entities = introspections.stream()
            // filter out inner / internal / abstract(MappedSuperClass) classes
            .filter(i -> !i.getBeanType().getName().contains("$"))
            .filter(i -> !java.lang.reflect.Modifier.isAbstract(i.getBeanType().getModifiers()))
            .map(e -> runtimeEntityRegistry.getEntity(e.getBeanType())).toArray(PersistentEntity[]::new);
        if (ArrayUtils.isEmpty(entities)) {
            LOG.warn("Did not find any mapped entity to process");
            return;
        }
        Map<String, CosmosContainerSettings> cosmosContainerSettings = configuration.getCosmosContainerSettings();
        for (PersistentEntity persistentEntity : entities) {
            initContainer(cosmosContainerSettings, configuration.getUpdatePolicy(), persistentEntity, cosmosDatabase);
        }
    }

    private void initContainer(Map<String, CosmosContainerSettings> cosmosContainerSettingsMap, StorageUpdatePolicy updatePolicy, PersistentEntity entity, CosmosDatabase cosmosDatabase) {
        String containerName = entity.getPersistedName();
        CosmosContainerSettings cosmosContainerSettings = cosmosContainerSettingsMap.get(containerName);
        String partitionKey = getPartitionKey(cosmosContainerSettings, entity);
        CosmosContainerProperties containerProperties = new CosmosContainerProperties(containerName, partitionKey);
        ThroughputSettings throughputSettings = cosmosContainerSettings != null ? cosmosContainerSettings.getThroughputSettings() : null;
        ThroughputProperties throughputProperties = createThroughputProperties(throughputSettings);
        if (StorageUpdatePolicy.CREATE_IF_NOT_EXISTS.equals(updatePolicy)) {
            if (throughputProperties == null) {
                cosmosDatabase.createContainerIfNotExists(containerProperties);
            } else {
                cosmosDatabase.createContainerIfNotExists(containerProperties, throughputProperties);
            }
        } else if (StorageUpdatePolicy.UPDATE.equals(updatePolicy) && throughputProperties != null) {
            CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerName);
            cosmosContainer.replaceThroughput(throughputProperties);
        }
    }

    private String getPartitionKey(CosmosContainerSettings cosmosContainerSettings, PersistentEntity entity) {
        String partitionKey;
        if (cosmosContainerSettings != null && StringUtils.isNotEmpty(cosmosContainerSettings.getPartitionKeyPath())) {
            partitionKey = cosmosContainerSettings.getPartitionKeyPath();
        } else {
            partitionKey = findPartitionKey(entity);
        }
        if (StringUtils.isNotEmpty(partitionKey)) {
            if (!partitionKey.startsWith("/")) {
                partitionKey = "/" + partitionKey;
            }
            return partitionKey;
        }
        return "/null";
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
            AnnotationValue<PartitionKey> partitionKeyAnnotationValue =
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
