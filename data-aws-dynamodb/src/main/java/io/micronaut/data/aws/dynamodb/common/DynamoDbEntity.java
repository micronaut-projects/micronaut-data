/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.aws.dynamodb.common;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.aws.dynamodb.annotation.IndexPartitionKey;
import io.micronaut.data.aws.dynamodb.annotation.IndexSortKey;
import io.micronaut.data.aws.dynamodb.annotation.PartitionKey;
import io.micronaut.data.aws.dynamodb.annotation.SortKey;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model holding DynamoDB entity fields like partition key, sort key, version field, indexes.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Internal
public final class DynamoDbEntity {

    private static final Map<RuntimePersistentEntity<?>, DynamoDbEntity> DYNAMODB_ENTITY_BY_PERSISTENT_ENTITY = new ConcurrentHashMap<>();

    private final String partitionKey;
    private final String sortKey;
    private final String versionField;
    private Map<String, List<IndexField>> indexes;

    private DynamoDbEntity(@NonNull String partitionKey, @Nullable String sortKey, @Nullable String versionField, Map<String, List<IndexField>> indexes) {
        this.partitionKey = partitionKey;
        this.sortKey = sortKey;
        this.versionField = versionField;
        this.indexes = indexes;
    }

    /**
     * Gets {@link DynamoDbEntity} for given {@link RuntimePersistentEntity}.
     *
     * @param runtimePersistentEntity the runtime persistent entity
     * @return the {@link DynamoDbEntity} holding mapped entity/dynamo db metadata
     */
    @NonNull
    public static DynamoDbEntity get(@NonNull RuntimePersistentEntity<?> runtimePersistentEntity) {
        return DYNAMODB_ENTITY_BY_PERSISTENT_ENTITY.computeIfAbsent(runtimePersistentEntity, e -> createDynamoDbEntity(runtimePersistentEntity));
    }

    private static DynamoDbEntity createDynamoDbEntity(RuntimePersistentEntity<?> runtimePersistentEntity) {
        NamingStrategy namingStrategy = runtimePersistentEntity.findNamingStrategy().orElse(new NamingStrategies.Raw());
        String partitionKey = null;
        String sortKey = null;
        if (runtimePersistentEntity.hasCompositeIdentity()) {
            throw new IllegalStateException("Composite key not allowed in DynamoDB found in entity " + runtimePersistentEntity.getName());
        } else if (!runtimePersistentEntity.hasIdentity()) {
            throw new IllegalStateException("Entity " + runtimePersistentEntity.getName() + " does not have identity");
        } else {
            // Figure out partition key
            RuntimePersistentProperty<?> identity = runtimePersistentEntity.getIdentity();
            if (identity.getAnnotationMetadata().hasStereotype(EmbeddedId.class)) {
                // Embedded id, need to find partition key and sort key parts
                if (identity instanceof Embedded embedded) {
                    PersistentEntity embeddedEntity = embedded.getAssociatedEntity();
                    Collection<? extends PersistentProperty> embeddedProperties = embeddedEntity.getPersistentProperties();
                    for (PersistentProperty property : embeddedProperties) {
                        if (property.getAnnotationMetadata().hasStereotype(PartitionKey.class)) {
                            if (partitionKey != null) {
                                throw new IllegalStateException("@PartitionKey cannot be annotated on " + property.getName() + " since it is already annotated on " + partitionKey);
                            }
                            partitionKey = getAnnotationValue(property, PartitionKey.class, namingStrategy);
                        } else if (property.getAnnotationMetadata().hasStereotype(SortKey.class)) {
                            if (sortKey != null) {
                                throw new IllegalStateException("@SortKey cannot be annotated on " + property.getName() + " since it is already annotated on " + sortKey);
                            }
                            sortKey = getAnnotationValue(property, SortKey.class, namingStrategy);
                        }
                    }
                }
            } else {
                // Just partition key
                // Maybe we shouldn't force PartitionKey - if there is @Id on simple field treat it as @PartitionKey
                // Or if there is @PartitionKey treat it as @Id ?
                if (!identity.getAnnotationMetadata().hasStereotype(PartitionKey.class)) {
                    throw new IllegalStateException("Id field " + runtimePersistentEntity.getName() + " must have @PartitionKey annotation");
                }
                partitionKey = getAnnotationValue(identity, PartitionKey.class, namingStrategy);
            }
        }
        RuntimePersistentProperty<?> versionProperty = runtimePersistentEntity.getVersion();
        String versionField = versionProperty != null ? namingStrategy.mappedName(versionProperty) : null;
        List<PersistentProperty> properties = new ArrayList<>(runtimePersistentEntity.getPersistentProperties());
        Map<String, List<IndexField>> indexes = findIndexes(properties, namingStrategy);
        DynamoDbEntity dynamoDbEntity = new DynamoDbEntity(partitionKey, sortKey, versionField, indexes);
        return dynamoDbEntity;
    }

    private static String getAnnotationValue(PersistentProperty property, Class<?> annotationClass, NamingStrategy namingStrategy) {
        AnnotationValue<PartitionKey> partitionKeyAnnotationValue =
            property.getAnnotation(annotationClass.getName());
        String partitionKeyValue = partitionKeyAnnotationValue.stringValue("value").orElse("");
        String value;
        if (StringUtils.isNotEmpty(partitionKeyValue)) {
            value = partitionKeyValue;
        } else {
            value = namingStrategy.mappedName(property);
        }
        return value;
    }

    private static Map<String, List<IndexField>> findIndexes(List<PersistentProperty> properties, NamingStrategy namingStrategy) {
        Map<String, List<IndexField>> indexes = new HashMap<>();
        for (PersistentProperty property : properties) {
            if (property.getAnnotationMetadata().hasStereotype(IndexPartitionKey.class)) {
                // This is used only for global secondary index
                // Not sure if alternate field name is needed
                String fieldName = getAnnotationValue(property, IndexPartitionKey.class, namingStrategy);
                List<IndexField> fieldIndexes = indexes.getOrDefault(property.getName(), new ArrayList<>());
                String[] indexNames = property.getAnnotationMetadata().stringValues(IndexPartitionKey.class, "globalSecondaryIndexNames");
                for (String indexName : indexNames) {
                    fieldIndexes.add(new IndexField(false, indexName, fieldName, IndexFieldRole.PARTITION_KEY));
                }
                indexes.put(property.getName(), fieldIndexes);
            } else if (property.getAnnotationMetadata().hasStereotype(IndexSortKey.class)) {
                // Used as sort key for both local and global secondary index
                String fieldName = getAnnotationValue(property, IndexSortKey.class, namingStrategy);
                List<IndexField> fieldIndexes = indexes.getOrDefault(property.getName(), new ArrayList<>());
                String[] indexNames = property.getAnnotationMetadata().stringValues(IndexSortKey.class, "globalSecondaryIndexNames");
                for (String indexName : indexNames) {
                    fieldIndexes.add(new IndexField(false, indexName, fieldName, IndexFieldRole.SORT_KEY));
                }
                indexNames = property.getAnnotationMetadata().stringValues(IndexSortKey.class, "localSecondaryIndexNames");
                for (String indexName : indexNames) {
                    fieldIndexes.add(new IndexField(true, indexName, fieldName, IndexFieldRole.SORT_KEY));
                }
                indexes.put(property.getName(), fieldIndexes);
            }
        }
        // TODO: Validate if some index does have sort key but not partition key
        // For local index, it will represent sort key on given field
        return indexes;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getSortKey() {
        return sortKey;
    }

    public String getVersionField() {
        return versionField;
    }

    /**
     * Gets indexes in DynamoDB table defined for given field in the mapped entity.
     *
     * @param field the field name
     * @return list of indexed for given field, may be empty
     */
    public List<IndexField> getIndexesByField(String field) {
        return indexes.getOrDefault(field, Collections.emptyList());
    }

    /**
     * @return the map of index fields by field name
     */
    public Map<String, List<IndexField>> getIndexes() {
        return Collections.unmodifiableMap(indexes);
    }

    /**
     * Representation of index definition in DynamoDB.
     *
     * @param local whether it is local or global secondary index
     * @param indexName the index name
     * @param fieldName the field name (attribute of the index)
     * @param indexFieldRole hash or range
     */
    public record IndexField(boolean local, String indexName, String fieldName, IndexFieldRole indexFieldRole) {
    }

    public enum IndexFieldRole {
        PARTITION_KEY,
        SORT_KEY
    }
}
