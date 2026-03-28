/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.mongodb.common;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.mongodb.annotation.MongoCompoundIndex;
import io.micronaut.data.mongodb.annotation.MongoCompoundIndexField;
import io.micronaut.data.mongodb.annotation.MongoIndexDirection;
import io.micronaut.data.mongodb.annotation.MongoGeoIndexed;
import io.micronaut.data.mongodb.annotation.MongoGeoIndexType;
import io.micronaut.data.mongodb.annotation.MongoHashedIndexed;
import io.micronaut.data.mongodb.annotation.MongoIndexed;
import io.micronaut.data.mongodb.annotation.MongoTextIndexed;
import io.micronaut.data.mongodb.annotation.MongoWildcardIndex;
import io.micronaut.data.mongodb.annotation.MongoWildcardIndexed;
import io.micronaut.data.mongodb.geo.MongoGeoConverters;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached Mongo index metadata resolved at runtime.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Internal
public final class MongoEntityIndexes {

    private static final Map<RuntimePersistentEntity<?>, MongoEntityIndexes> INDEXES_BY_ENTITY = new ConcurrentHashMap<>();

    private final List<ResolvedIndex> indexes;

    private MongoEntityIndexes(List<ResolvedIndex> indexes) {
        this.indexes = indexes;
    }

    /**
     * Resolve Mongo indexes for the entity.
     *
     * @param entity The runtime entity
     * @return The resolved indexes
     */
    public static MongoEntityIndexes create(RuntimePersistentEntity<?> entity) {
        return INDEXES_BY_ENTITY.computeIfAbsent(entity, MongoEntityIndexes::resolve);
    }

    /**
     * @return The resolved indexes.
     */
    public List<ResolvedIndex> getIndexes() {
        return indexes;
    }

    private static MongoEntityIndexes resolve(RuntimePersistentEntity<?> entity) {
        List<ResolvedIndex> indexes = new ArrayList<>();
        indexes.addAll(resolveFieldIndexes(entity));
        indexes.addAll(resolveTopLevelWildcardIndexes(entity));
        indexes.addAll(resolveTextIndexes(entity));
        indexes.addAll(resolveCompoundIndexes(entity));
        return new MongoEntityIndexes(List.copyOf(indexes));
    }

    private static List<ResolvedIndex> resolveTopLevelWildcardIndexes(RuntimePersistentEntity<?> entity) {
        List<ResolvedIndex> indexes = new ArrayList<>();
        var annotation = entity.getAnnotationMetadata().getAnnotation(MongoWildcardIndex.class);
        if (annotation != null) {
            indexes.add(new ResolvedIndex(
                    annotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                    List.of(new ResolvedIndexField("$**", 1, null, null, null, null)),
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    annotation.stringValue("wildcardProjection").filter(s -> !s.isEmpty()).orElse(null)
            ));
        }
        return indexes;
    }

    private static List<ResolvedIndex> resolveFieldIndexes(RuntimePersistentEntity<?> entity) {
        List<ResolvedIndex> indexes = new ArrayList<>();
        BeanIntrospection<?> introspection = entity.getIntrospection();
        for (BeanProperty<?, Object> beanProperty : introspection.getBeanProperties()) {
            RuntimePersistentProperty<?> property = entity.getPropertyByName(beanProperty.getName());
            if (property == null || property instanceof Association) {
                continue;
            }
            var annotation = beanProperty.getAnnotationMetadata().getAnnotation(MongoIndexed.class);
            if (annotation != null) {
                indexes.add(new ResolvedIndex(
                        annotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                        List.of(new ResolvedIndexField(property.getPersistedName(), annotation.enumValue("direction", MongoIndexDirection.class).orElse(MongoIndexDirection.ASC) == MongoIndexDirection.DESC ? -1 : 1, null, null, null, null)),
                        annotation.booleanValue("unique").orElse(false),
                        annotation.booleanValue("sparse").orElse(false),
                        annotation.intValue("expireAfterSeconds").isPresent() && annotation.intValue("expireAfterSeconds").getAsInt() >= 0 ? annotation.intValue("expireAfterSeconds").getAsInt() : null,
                        annotation.stringValue("partialFilterExpression").filter(s -> !s.isEmpty()).orElse(null),
                        annotation.stringValue("collation").filter(s -> !s.isEmpty()).orElse(null),
                        null,
                        null,
                        null,
                        null
                ));
                continue;
            }
            var textAnnotation = beanProperty.getAnnotationMetadata().getAnnotation(MongoTextIndexed.class);
            if (textAnnotation != null) {
                continue;
            }
            var hashedAnnotation = beanProperty.getAnnotationMetadata().getAnnotation(MongoHashedIndexed.class);
            if (hashedAnnotation != null) {
                indexes.add(new ResolvedIndex(
                        hashedAnnotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                        List.of(new ResolvedIndexField(property.getPersistedName(), null, null, "hashed", null, null)),
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
                continue;
            }
            var geoAnnotation = beanProperty.getAnnotationMetadata().getAnnotation(MongoGeoIndexed.class);
            if (geoAnnotation != null) {
                validateGeoIndexedType(entity, property);
                MongoGeoIndexType type = geoAnnotation.enumValue("type", MongoGeoIndexType.class).orElse(MongoGeoIndexType.GEO_2DSPHERE);
                Integer bits = geoAnnotation.intValue("bits").isPresent() && geoAnnotation.intValue("bits").getAsInt() >= 0 ? geoAnnotation.intValue("bits").getAsInt() : null;
                Double min = geoAnnotation.doubleValue("min").isPresent() && !Double.isNaN(geoAnnotation.doubleValue("min").getAsDouble()) ? geoAnnotation.doubleValue("min").getAsDouble() : null;
                Double max = geoAnnotation.doubleValue("max").isPresent() && !Double.isNaN(geoAnnotation.doubleValue("max").getAsDouble()) ? geoAnnotation.doubleValue("max").getAsDouble() : null;
                if (type != MongoGeoIndexType.GEO_2D && (bits != null || min != null || max != null)) {
                    throw new IllegalStateException("2d-specific geospatial options are only supported for Mongo 2d indexes on entity [" + entity.getName() + "]");
                }
                indexes.add(new ResolvedIndex(
                        geoAnnotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                        List.of(new ResolvedIndexField(property.getPersistedName(), null, null, type.getKey(), min, max)),
                        false,
                        false,
                        null,
                        null,
                        null,
                        bits,
                        min,
                        max,
                        null
                ));
                continue;
            }
            var wildcardAnnotation = beanProperty.getAnnotationMetadata().getAnnotation(MongoWildcardIndexed.class);
            if (wildcardAnnotation != null) {
                String wildcardProjection = wildcardAnnotation.stringValue("wildcardProjection").filter(s -> !s.isEmpty()).orElse(null);
                if (wildcardProjection != null) {
                    throw new IllegalStateException("Mongo wildcardProjection on field-level @MongoWildcardIndexed is not supported by MongoDB. Use @MongoWildcardIndex on the entity instead for top-level wildcard projection.");
                }
                indexes.add(new ResolvedIndex(
                        wildcardAnnotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                        List.of(new ResolvedIndexField(property.getPersistedName() + ".$**", 1, null, null, null, null)),
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
        }
        return indexes;
    }

    private static void validateGeoIndexedType(RuntimePersistentEntity<?> entity,
                                               RuntimePersistentProperty<?> property) {
        Class<?> propertyType = property.getType();
        if (MongoGeoConverters.supportsGeoIndexedPropertyType(propertyType)) {
            return;
        }
        throw new IllegalStateException("Mongo geospatial index on entity ["
                + entity.getName()
                + "] property ["
                + property.getName()
                + "] requires a supported type (MongoGeoPoint, MongoGeoPointLike, point-like bean shape, MongoGeoMultiPoint, MongoGeoLineString, MongoGeoMultiLineString, MongoGeoPolygon, MongoGeoMultiPolygon, or MongoGeoGeometryCollection)");
    }

    private static List<ResolvedIndex> resolveTextIndexes(RuntimePersistentEntity<?> entity) {
        List<ResolvedIndexField> fields = new ArrayList<>();
        String name = null;
        BeanIntrospection<?> introspection = entity.getIntrospection();
        for (BeanProperty<?, Object> beanProperty : introspection.getBeanProperties()) {
            RuntimePersistentProperty<?> property = entity.getPropertyByName(beanProperty.getName());
            if (property == null || property instanceof Association) {
                continue;
            }
            var textAnnotation = beanProperty.getAnnotationMetadata().getAnnotation(MongoTextIndexed.class);
            if (textAnnotation != null) {
                int weight = textAnnotation.intValue("weight").orElse(1);
                if (weight <= 0) {
                    throw new IllegalStateException("Mongo text index weight must be greater than zero for entity [" + entity.getName() + "]");
                }
                String declaredName = textAnnotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null);
                if (name == null) {
                    name = declaredName;
                } else if (declaredName != null && !declaredName.equals(name)) {
                    throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same index name");
                }
                fields.add(new ResolvedIndexField(property.getPersistedName(), null, weight, "text", null, null));
            }
        }
        if (fields.isEmpty()) {
            return List.of();
        }
        return List.of(new ResolvedIndex(name, List.copyOf(fields), false, false, null, null, null, null, null, null, null));
    }

    private static List<ResolvedIndex> resolveCompoundIndexes(RuntimePersistentEntity<?> entity) {
        List<ResolvedIndex> indexes = new ArrayList<>();
        for (var annotationValue : entity.getAnnotationMetadata().getAnnotationValuesByType(MongoCompoundIndex.class)) {
            List<ResolvedIndexField> fields = new ArrayList<>();
            java.util.Set<String> seenPaths = new java.util.LinkedHashSet<>();
            Integer indexBits = null;
            Double indexMin = null;
            Double indexMax = null;
            for (var fieldAnnotation : annotationValue.getAnnotations("fields", MongoCompoundIndexField.class)) {
                String path = fieldAnnotation.stringValue().orElseThrow();
                String persistedPath = PersistentEntityUtils.getPersistentPropertyPath(entity, path)
                        .map(persistentPath -> {
                            var propertyPath = entity.getPropertyPath(persistentPath);
                            if (propertyPath == null) {
                                throw new IllegalStateException("Invalid Mongo index path [" + path + "] for entity [" + entity.getName() + "]");
                            }
                            StringBuilder resolved = new StringBuilder();
                            for (Association association : propertyPath.getAssociations()) {
                                if (!resolved.isEmpty()) {
                                    resolved.append('.');
                                }
                                resolved.append(association.getPersistedName());
                            }
                            if (!resolved.isEmpty()) {
                                resolved.append('.');
                            }
                            resolved.append(propertyPath.getProperty().getPersistedName());
                            return resolved.toString();
                        })
                        .orElseThrow(() -> new IllegalStateException("Invalid Mongo index path [" + path + "] for entity [" + entity.getName() + "]"));
                if (!seenPaths.add(persistedPath)) {
                    throw new IllegalStateException("Duplicate Mongo index path [" + persistedPath + "] for entity [" + entity.getName() + "]");
                }
                boolean geo = fieldAnnotation.booleanValue("geo").orElse(false);
                MongoIndexDirection direction = fieldAnnotation.enumValue("direction", MongoIndexDirection.class).orElse(MongoIndexDirection.ASC);
                if (geo) {
                    if (direction != MongoIndexDirection.ASC) {
                        throw new IllegalStateException("Mongo compound geospatial field [" + persistedPath + "] on entity [" + entity.getName() + "] cannot define a numeric direction");
                    }
                    MongoGeoIndexType geoType = fieldAnnotation.enumValue("geoType", MongoGeoIndexType.class).orElse(MongoGeoIndexType.GEO_2DSPHERE);
                    Integer bits = fieldAnnotation.intValue("bits").isPresent() && fieldAnnotation.intValue("bits").getAsInt() >= 0 ? fieldAnnotation.intValue("bits").getAsInt() : null;
                    Double min = fieldAnnotation.doubleValue("min").isPresent() && !Double.isNaN(fieldAnnotation.doubleValue("min").getAsDouble()) ? fieldAnnotation.doubleValue("min").getAsDouble() : null;
                    Double max = fieldAnnotation.doubleValue("max").isPresent() && !Double.isNaN(fieldAnnotation.doubleValue("max").getAsDouble()) ? fieldAnnotation.doubleValue("max").getAsDouble() : null;
                    if (geoType != MongoGeoIndexType.GEO_2D && (bits != null || min != null || max != null)) {
                        throw new IllegalStateException("2d-specific geospatial options are only supported for Mongo 2d compound geospatial fields on entity [" + entity.getName() + "]");
                    }
                    if (bits != null) {
                        if (indexBits != null && !indexBits.equals(bits)) {
                            throw new IllegalStateException("Mongo compound index on entity [" + entity.getName() + "] declares conflicting bits options for geospatial fields");
                        }
                        indexBits = bits;
                    }
                    if (min != null) {
                        if (indexMin != null && !indexMin.equals(min)) {
                            throw new IllegalStateException("Mongo compound index on entity [" + entity.getName() + "] declares conflicting min options for geospatial fields");
                        }
                        indexMin = min;
                    }
                    if (max != null) {
                        if (indexMax != null && !indexMax.equals(max)) {
                            throw new IllegalStateException("Mongo compound index on entity [" + entity.getName() + "] declares conflicting max options for geospatial fields");
                        }
                        indexMax = max;
                    }
                    fields.add(new ResolvedIndexField(persistedPath, null, null, geoType.getKey(), min, max));
                } else {
                    if ((fieldAnnotation.intValue("bits").isPresent() && fieldAnnotation.intValue("bits").getAsInt() >= 0)
                            || (fieldAnnotation.doubleValue("min").isPresent() && !Double.isNaN(fieldAnnotation.doubleValue("min").getAsDouble()))
                            || (fieldAnnotation.doubleValue("max").isPresent() && !Double.isNaN(fieldAnnotation.doubleValue("max").getAsDouble()))) {
                        throw new IllegalStateException("2d-specific geospatial options require geo=true for Mongo compound index field [" + persistedPath + "] on entity [" + entity.getName() + "]");
                    }
                    fields.add(new ResolvedIndexField(persistedPath, direction == MongoIndexDirection.DESC ? -1 : 1, null, null, null, null));
                }
            }
            if (fields.isEmpty()) {
                throw new IllegalStateException("Mongo compound index on entity [" + entity.getName() + "] must declare at least one field");
            }
            if (annotationValue.intValue("expireAfterSeconds").isPresent() && annotationValue.intValue("expireAfterSeconds").getAsInt() >= 0) {
                throw new IllegalStateException("TTL is not supported for Mongo compound index on entity [" + entity.getName() + "]");
            }
            String partialFilterExpression = annotationValue.stringValue("partialFilterExpression").filter(s -> !s.isEmpty()).orElse(null);
            boolean sparse = annotationValue.booleanValue("sparse").orElse(false);
            if (sparse && partialFilterExpression != null) {
                throw new IllegalStateException("Mongo compound index on entity [" + entity.getName() + "] cannot define both sparse and partialFilterExpression");
            }
            indexes.add(new ResolvedIndex(
                    annotationValue.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                    List.copyOf(fields),
                    annotationValue.booleanValue("unique").orElse(false),
                    sparse,
                    null,
                    partialFilterExpression,
                    annotationValue.stringValue("collation").filter(s -> !s.isEmpty()).orElse(null),
                    indexBits,
                    indexMin,
                    indexMax,
                    null
            ));
        }
        return indexes;
    }

    /**
     * Resolved Mongo index definition.
     *
     * @param name The index name
     * @param fields The index fields
     * @param unique Whether unique
     * @param sparse Whether sparse
     * @param expireAfterSeconds TTL in seconds if any
     * @param partialFilterExpression The partial filter expression JSON
     * @param collation The collation JSON
     * @param bits The geospatial bits option for 2d indexes
     * @param min The geospatial min option for 2d indexes
     * @param max The geospatial max option for 2d indexes
     * @param wildcardProjection The wildcard projection JSON
     */
    public record ResolvedIndex(@Nullable String name,
                                List<ResolvedIndexField> fields,
                                boolean unique,
                                boolean sparse,
                                @Nullable Integer expireAfterSeconds,
                                @Nullable String partialFilterExpression,
                                @Nullable String collation,
                                @Nullable Integer bits,
                                @Nullable Double min,
                                @Nullable Double max,
                                @Nullable String wildcardProjection) {
    }

    /**
     * Resolved Mongo index field.
     *
     * @param path The persisted path
     * @param order The field order
     * @param weight The text index weight
     * @param kind The index kind
     * @param min The geospatial min option for 2d indexes
     * @param max The geospatial max option for 2d indexes
     */
    public record ResolvedIndexField(String path, @Nullable Integer order, @Nullable Integer weight, @Nullable String kind, @Nullable Double min, @Nullable Double max) {
    }
}
