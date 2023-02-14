package io.micronaut.data.aws.dynamodb.utils;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.aws.dynamodb.common.DynamoDbEntity;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The table utils used for tests.
 */
public class TableUtils {

    public static boolean createTable(AmazonDynamoDB amazonDynamoDB, RuntimePersistentEntity persistentEntity, DynamoDbEntity dynamoDbEntity) {
        NamingStrategy namingStrategy = persistentEntity.findNamingStrategy().orElse(new NamingStrategies.Raw());
        CreateTableRequest createTableRequest = new CreateTableRequest();
        createTableRequest.setTableName(namingStrategy.mappedName(persistentEntity));
        List<KeySchemaElement> keySchemaElements = new ArrayList<>(2);
        KeySchemaElement primaryKeyPartitionKey = new KeySchemaElement();
        primaryKeyPartitionKey.setKeyType(KeyType.HASH);
        primaryKeyPartitionKey.setAttributeName(dynamoDbEntity.getPartitionKey());
        keySchemaElements.add(primaryKeyPartitionKey);
        if (StringUtils.isNotEmpty(dynamoDbEntity.getSortKey())) {
            KeySchemaElement primaryKeySortKey = new KeySchemaElement();
            primaryKeySortKey.setKeyType(KeyType.RANGE);
            primaryKeySortKey.setAttributeName(dynamoDbEntity.getSortKey());
            keySchemaElements.add(primaryKeySortKey);
        }
        createTableRequest.setKeySchema(keySchemaElements);
        ProvisionedThroughput provisionedThroughput = new ProvisionedThroughput();
        provisionedThroughput.setReadCapacityUnits(1L);
        provisionedThroughput.setWriteCapacityUnits(1L);
        createTableRequest.setProvisionedThroughput(provisionedThroughput);

        Map<String, List<DynamoDbEntity.IndexField>> indexes = dynamoDbEntity.getIndexes();
        if (CollectionUtils.isNotEmpty(indexes)) {
            Map<String, GlobalSecondaryIndex> globalSecondaryIndexes = new HashMap<>();
            Map<String, LocalSecondaryIndex> localSecondaryIndexes = new HashMap<>();
            for (Map.Entry<String, List<DynamoDbEntity.IndexField>> indexEntry : indexes.entrySet()) {
                List<DynamoDbEntity.IndexField> indexFields = indexEntry.getValue();
                for (DynamoDbEntity.IndexField indexField : indexFields) {
                    String indexName = indexField.indexName();
                    if (indexField.local()) {
                        LocalSecondaryIndex localSecondaryIndex = localSecondaryIndexes.get(indexName);
                        if (localSecondaryIndex == null) {
                            localSecondaryIndex = new LocalSecondaryIndex();
                        }
                        localSecondaryIndex.setIndexName(indexName);
                        List<KeySchemaElement> indexKeySchemaElements = localSecondaryIndex.getKeySchema();
                        if (indexKeySchemaElements == null) {
                            indexKeySchemaElements = new ArrayList<>();
                        }
                        KeySchemaElement indexKeySchemaElement = new KeySchemaElement();
                        indexKeySchemaElement.setAttributeName(indexField.fieldName());
                        // For local index, it is only range, hash is partition key from primary key
                        indexKeySchemaElement.setKeyType(KeyType.RANGE);
                        indexKeySchemaElements.add(indexKeySchemaElement);
                        localSecondaryIndex.setKeySchema(indexKeySchemaElements);
                        localSecondaryIndexes.put(indexName, localSecondaryIndex);
                    } else {
                        GlobalSecondaryIndex globalSecondaryIndex = globalSecondaryIndexes.get(indexName);
                        if (globalSecondaryIndex == null) {
                            globalSecondaryIndex = new GlobalSecondaryIndex();
                            ProvisionedThroughput gsiProvisionedThroughput = new ProvisionedThroughput();
                            gsiProvisionedThroughput.setReadCapacityUnits(1L);
                            gsiProvisionedThroughput.setWriteCapacityUnits(1L);
                            globalSecondaryIndex.setProvisionedThroughput(gsiProvisionedThroughput);
                            Projection projection = new Projection();
                            projection.setProjectionType(ProjectionType.ALL);
                            globalSecondaryIndex.setProjection(projection);
                        }
                        globalSecondaryIndex.setIndexName(indexName);
                        List<KeySchemaElement> indexKeySchemaElements = globalSecondaryIndex.getKeySchema();
                        if (indexKeySchemaElements == null) {
                            indexKeySchemaElements = new ArrayList<>();
                        }
                        KeySchemaElement indexKeySchemaElement = new KeySchemaElement();
                        indexKeySchemaElement.setAttributeName(indexField.fieldName());
                        indexKeySchemaElement.setKeyType(indexField.indexFieldRole().equals(DynamoDbEntity.IndexFieldRole.PARTITION_KEY) ? KeyType.HASH : KeyType.RANGE );
                        indexKeySchemaElements.add(indexKeySchemaElement);
                        globalSecondaryIndex.setKeySchema(indexKeySchemaElements);
                        globalSecondaryIndexes.put(indexName, globalSecondaryIndex);
                    }
                }
            }
            createTableRequest.setGlobalSecondaryIndexes(globalSecondaryIndexes.values());
            createTableRequest.setLocalSecondaryIndexes(localSecondaryIndexes.values());
        }

        List<PersistentProperty> properties = new ArrayList<>(persistentEntity.getPersistentProperties());
        PersistentProperty identity = persistentEntity.getIdentity();
        if (identity != null) {
            if (identity instanceof Embedded embedded) {
                properties.addAll(embedded.getAssociatedEntity().getPersistentProperties());
            } else {
                properties.add(identity);
            }
        }
        List<AttributeDefinition> attributeDefinitions = new ArrayList<>(properties.size());
        for (PersistentProperty property : properties) {
            String propertyName = property.getName();
            boolean addField = propertyName.equals(dynamoDbEntity.getPartitionKey()) || propertyName.equals(dynamoDbEntity.getSortKey()) ||
                indexes.containsKey(propertyName);
            if (addField) {
                AttributeDefinition attributeDefinition = new AttributeDefinition();
                attributeDefinition.setAttributeName(namingStrategy.mappedName(property));
                attributeDefinition.setAttributeType(getAttributeType(property));
                attributeDefinitions.add(attributeDefinition);
            }
        }
        createTableRequest.setAttributeDefinitions(attributeDefinitions);
        return com.amazonaws.services.dynamodbv2.util.TableUtils.createTableIfNotExists(amazonDynamoDB, createTableRequest);
    }

    public static RuntimePersistentEntity[] findMappedEntities(RuntimeEntityRegistry runtimeEntityRegistry, List<String> packages) {
        return findMappedEntities(runtimeEntityRegistry, packages, null);
    }

    public static RuntimePersistentEntity[] findMappedEntities(RuntimeEntityRegistry runtimeEntityRegistry, List<String> packages, String className) {
        Collection<BeanIntrospection<Object>> introspections;
        if (CollectionUtils.isNotEmpty(packages)) {
            introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class, packages.toArray(new String[0]));
        } else {
            introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class);
        }
        return introspections.stream()
            // filter out inner / internal / abstract(MappedSuperClass) classes
            .filter(i -> !i.getBeanType().getName().contains("$"))
            .filter(i -> !Modifier.isAbstract(i.getBeanType().getModifiers()))
            .filter(i -> StringUtils.isNotEmpty(className) ? i.getBeanType().getName().equals(className) : true)
            .map(e -> runtimeEntityRegistry.getEntity(e.getBeanType())).toArray(RuntimePersistentEntity[]::new);
    }

    public static  <T> PutItemOutcome insertEntity(AmazonDynamoDB amazonDynamoDB, RuntimePersistentEntity persistentEntity, DynamoDbEntity dynamoDbEntity, T entity) {
        DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
        NamingStrategy namingStrategy = persistentEntity.findNamingStrategy().orElse(new NamingStrategies.Raw());
        String tableName = namingStrategy.mappedName(persistentEntity);
        Table table = dynamoDB.getTable(tableName);
        Item item = new Item();
        RuntimePersistentProperty identity = persistentEntity.getIdentity();
        if (identity instanceof Embedded embedded) {
            Object idValue = identity.getProperty().get(entity);
            for (PersistentProperty property : embedded.getAssociatedEntity().getPersistentProperties()) {
                Object value = ((RuntimePersistentProperty) property).getProperty().get(idValue);
                item.with(namingStrategy.mappedName(property), value);
            }
        } else {
            Object value = identity.getProperty().get(entity);
            item.withPrimaryKey(namingStrategy.mappedName(identity), value);
        }
        Collection<RuntimePersistentProperty> properties = persistentEntity.getPersistentProperties();
        for (RuntimePersistentProperty property : properties) {
            Object value = property.getProperty().get(entity);
            item.with(namingStrategy.mappedName(property), value);
        }

        PutItemOutcome putItemOutcome = table.putItem(item);
        return putItemOutcome;
    }

    private static String getAttributeType(PersistentProperty property) {
        switch (property.getDataType()) {
            case BIGDECIMAL:
            case BYTE:
            case FLOAT:
            case LONG:
            case SHORT:
            case INTEGER:
            case DOUBLE:
            case BOOLEAN:
            case TIMESTAMP:
                return "N";
            case STRING:
            case CHARACTER:
            case UUID:
            case JSON:
            case DATE:
            case TIME:
                return "S";
            case ENTITY:
            case OBJECT:
                return "B";
            default:
                throw new IllegalArgumentException("Unsupported data type in Micronaut Data AWS DynaomDB " + property.getDataType());
        }
    }
}
