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
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mongo index metadata resolved at runtime.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Internal
public final class MongoEntityIndexes {

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
        return resolve(entity);
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
        for (var annotation : entity.getAnnotationMetadata().getAnnotationValuesByType(MongoWildcardIndex.class)) {
            indexes.add(new ResolvedIndex(
                    annotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                    List.of(new ResolvedIndexField("$**", 1, null, null, null, null)),
                    false,
                    false,
                    annotation.booleanValue("hidden").orElse(false),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    annotation.stringValue("wildcardProjection").filter(s -> !s.isEmpty()).orElse(null),
                    parseJsonOption(annotation.stringValue("storageEngine").filter(s -> !s.isEmpty()).orElse(null), "storageEngine", entity.getName()),
                    annotation.stringValue("comment").filter(s -> !s.isEmpty()).orElse(null),
                    annotation.stringValue("commitQuorum").filter(s -> !s.isEmpty()).orElse(null)
            ));
        }
        if (indexes.size() <= 1) {
            return indexes;
        }
        ResolvedIndex first = indexes.getFirst();
        String mergedName = first.name();
        for (int i = 1; i < indexes.size(); i++) {
            ResolvedIndex candidate = indexes.get(i);
            if (!Objects.equals(first.fields(), candidate.fields())
                    || first.unique() != candidate.unique()
                    || first.sparse() != candidate.sparse()
                    || first.hidden() != candidate.hidden()
                    || !Objects.equals(first.expireAfterSeconds(), candidate.expireAfterSeconds())
                    || !Objects.equals(first.partialFilterExpression(), candidate.partialFilterExpression())
                    || !Objects.equals(first.collation(), candidate.collation())
                    || !Objects.equals(first.bits(), candidate.bits())
                    || !Objects.equals(first.min(), candidate.min())
                    || !Objects.equals(first.max(), candidate.max())
                    || !Objects.equals(first.defaultLanguage(), candidate.defaultLanguage())
                    || !Objects.equals(first.languageOverride(), candidate.languageOverride())
                    || !Objects.equals(first.textIndexVersion(), candidate.textIndexVersion())
                    || !Objects.equals(first.sphereVersion(), candidate.sphereVersion())
                    || !Objects.equals(first.wildcardProjection(), candidate.wildcardProjection())
                    || !Objects.equals(first.storageEngine(), candidate.storageEngine())
                    || !Objects.equals(first.comment(), candidate.comment())
                    || !Objects.equals(first.commitQuorum(), candidate.commitQuorum())) {
                throw new IllegalStateException("Mongo top-level wildcard indexes on entity [" + entity.getName() + "] declare conflicting options for key [$**]");
            }
            if (mergedName == null) {
                mergedName = candidate.name();
            } else if (candidate.name() != null && !mergedName.equals(candidate.name())) {
                throw new IllegalStateException("Mongo top-level wildcard indexes on entity [" + entity.getName() + "] must use the same index name when declaring equivalent key [$**]");
            }
        }
        return List.of(new ResolvedIndex(
                mergedName,
                first.fields(),
                first.unique(),
                first.sparse(),
                first.hidden(),
                first.expireAfterSeconds(),
                first.partialFilterExpression(),
                first.collation(),
                first.bits(),
                first.min(),
                first.max(),
                first.defaultLanguage(),
                first.languageOverride(),
                first.textIndexVersion(),
                first.sphereVersion(),
                first.wildcardProjection(),
                first.storageEngine(),
                first.comment(),
                first.commitQuorum()
        ));
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
                        annotation.booleanValue("hidden").orElse(false),
                        annotation.intValue("expireAfterSeconds").isPresent() && annotation.intValue("expireAfterSeconds").getAsInt() >= 0 ? annotation.intValue("expireAfterSeconds").getAsInt() : null,
                        annotation.stringValue("partialFilterExpression").filter(s -> !s.isEmpty()).orElse(null),
                        annotation.stringValue("collation").filter(s -> !s.isEmpty()).orElse(null),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        parseJsonOption(annotation.stringValue("storageEngine").filter(s -> !s.isEmpty()).orElse(null), "storageEngine", entity.getName()),
                        annotation.stringValue("comment").filter(s -> !s.isEmpty()).orElse(null),
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
                        hashedAnnotation.booleanValue("hidden").orElse(false),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        parseJsonOption(hashedAnnotation.stringValue("storageEngine").filter(s -> !s.isEmpty()).orElse(null), "storageEngine", entity.getName()),
                        hashedAnnotation.stringValue("comment").filter(s -> !s.isEmpty()).orElse(null),
                        null
                ));
                continue;
            }
            var geoAnnotation = beanProperty.getAnnotationMetadata().getAnnotation(MongoGeoIndexed.class);
            if (geoAnnotation != null) {
                validateGeoIndexedType(entity, property);
                MongoGeoIndexType type = geoAnnotation.enumValue("type", MongoGeoIndexType.class).orElse(MongoGeoIndexType.GEO_2DSPHERE);
                Integer sphereVersion = geoAnnotation.intValue("sphereVersion").isPresent() && geoAnnotation.intValue("sphereVersion").getAsInt() >= 0 ? geoAnnotation.intValue("sphereVersion").getAsInt() : null;
                Integer bits = geoAnnotation.intValue("bits").isPresent() && geoAnnotation.intValue("bits").getAsInt() >= 0 ? geoAnnotation.intValue("bits").getAsInt() : null;
                Double min = geoAnnotation.doubleValue("min").isPresent() && !Double.isNaN(geoAnnotation.doubleValue("min").getAsDouble()) ? geoAnnotation.doubleValue("min").getAsDouble() : null;
                Double max = geoAnnotation.doubleValue("max").isPresent() && !Double.isNaN(geoAnnotation.doubleValue("max").getAsDouble()) ? geoAnnotation.doubleValue("max").getAsDouble() : null;
                if (type != MongoGeoIndexType.GEO_2D && (bits != null || min != null || max != null)) {
                    throw new IllegalStateException("2d-specific geospatial options are only supported for Mongo 2d indexes on entity [" + entity.getName() + "]");
                }
                if (type != MongoGeoIndexType.GEO_2DSPHERE && sphereVersion != null) {
                    throw new IllegalStateException("2dsphere-specific geospatial options are only supported for Mongo 2dsphere indexes on entity [" + entity.getName() + "]");
                }
                indexes.add(new ResolvedIndex(
                        geoAnnotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                        List.of(new ResolvedIndexField(property.getPersistedName(), null, null, type.getKey(), min, max)),
                        false,
                        false,
                        geoAnnotation.booleanValue("hidden").orElse(false),
                        null,
                        null,
                        null,
                        bits,
                        min,
                        max,
                        null,
                        null,
                        null,
                        sphereVersion,
                        null,
                        parseJsonOption(geoAnnotation.stringValue("storageEngine").filter(s -> !s.isEmpty()).orElse(null), "storageEngine", entity.getName()),
                        geoAnnotation.stringValue("comment").filter(s -> !s.isEmpty()).orElse(null),
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
                        wildcardAnnotation.booleanValue("hidden").orElse(false),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        parseJsonOption(wildcardAnnotation.stringValue("storageEngine").filter(s -> !s.isEmpty()).orElse(null), "storageEngine", entity.getName()),
                        wildcardAnnotation.stringValue("comment").filter(s -> !s.isEmpty()).orElse(null),
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
        Boolean hidden = null;
        String storageEngine = null;
        String comment = null;
        String defaultLanguage = null;
        String languageOverride = null;
        Integer textIndexVersion = null;
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
                boolean declaredHidden = textAnnotation.booleanValue("hidden").orElse(false);
                if (hidden == null) {
                    hidden = declaredHidden;
                } else if (!hidden.equals(declaredHidden)) {
                    throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same hidden option");
                }
                String declaredComment = textAnnotation.stringValue("comment").filter(s -> !s.isEmpty()).orElse(null);
                if (comment == null) {
                    comment = declaredComment;
                } else if (!Objects.equals(comment, declaredComment)) {
                    throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same comment option");
                }
                String declaredStorageEngine = parseJsonOption(textAnnotation.stringValue("storageEngine").filter(s -> !s.isEmpty()).orElse(null), "storageEngine", entity.getName());
                if (storageEngine == null) {
                    storageEngine = declaredStorageEngine;
                } else if (!Objects.equals(storageEngine, declaredStorageEngine)) {
                    throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same storageEngine option");
                }
                String declaredDefaultLanguage = textAnnotation.stringValue("defaultLanguage").filter(s -> !s.isEmpty()).orElse(null);
                if (defaultLanguage == null) {
                    defaultLanguage = declaredDefaultLanguage;
                } else if (!Objects.equals(defaultLanguage, declaredDefaultLanguage)) {
                    throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same defaultLanguage option");
                }
                String declaredLanguageOverride = textAnnotation.stringValue("languageOverride").filter(s -> !s.isEmpty()).orElse(null);
                if (languageOverride == null) {
                    languageOverride = declaredLanguageOverride;
                } else if (!Objects.equals(languageOverride, declaredLanguageOverride)) {
                    throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same languageOverride option");
                }
                Integer declaredTextIndexVersion = textAnnotation.intValue("textIndexVersion").isPresent() && textAnnotation.intValue("textIndexVersion").getAsInt() >= 0
                        ? textAnnotation.intValue("textIndexVersion").getAsInt() : null;
                if (declaredTextIndexVersion != null && declaredTextIndexVersion <= 0) {
                    throw new IllegalStateException("Mongo text index version must be greater than zero for entity [" + entity.getName() + "]");
                }
                if (textIndexVersion == null) {
                    textIndexVersion = declaredTextIndexVersion;
                } else if (!Objects.equals(textIndexVersion, declaredTextIndexVersion)) {
                    throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same textIndexVersion option");
                }
                fields.add(new ResolvedIndexField(property.getPersistedName(), null, weight, "text", null, null));
            }
        }
        if (fields.isEmpty()) {
            return List.of();
        }
        return List.of(new ResolvedIndex(name, List.copyOf(fields), false, false, hidden != null && hidden, null, null, null, null, null, null, defaultLanguage, languageOverride, textIndexVersion, null, null, storageEngine, comment, null));
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
                    annotationValue.booleanValue("hidden").orElse(false),
                    null,
                    partialFilterExpression,
                    annotationValue.stringValue("collation").filter(s -> !s.isEmpty()).orElse(null),
                    indexBits,
                    indexMin,
                    indexMax,
                    null,
                    null,
                    null,
                    null,
                    null,
                    parseJsonOption(annotationValue.stringValue("storageEngine").filter(s -> !s.isEmpty()).orElse(null), "storageEngine", entity.getName()),
                    annotationValue.stringValue("comment").filter(s -> !s.isEmpty()).orElse(null),
                    annotationValue.stringValue("commitQuorum").filter(s -> !s.isEmpty()).orElse(null)
            ));
        }
        return indexes;
    }

    private static @Nullable String parseJsonOption(@Nullable String json,
                                                    String option,
                                                    String entityName) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return Document.parse(json).toJson();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Mongo " + option + " for entity [" + entityName + "] must be valid JSON", e);
        }
    }

    /**
     * Resolved Mongo index definition.
     *
     * @param name The index name
     * @param fields The index fields
     * @param unique Whether unique
     * @param sparse Whether sparse
     * @param hidden Whether hidden
     * @param expireAfterSeconds TTL in seconds if any
     * @param partialFilterExpression The partial filter expression JSON
     * @param collation The collation JSON
     * @param bits The geospatial bits option for 2d indexes
     * @param min The geospatial min option for 2d indexes
     * @param max The geospatial max option for 2d indexes
     * @param defaultLanguage The text index default language
     * @param languageOverride The text index language override field
     * @param textIndexVersion The text index version
     * @param sphereVersion The 2dsphere index version
     * @param wildcardProjection The wildcard projection JSON
     * @param storageEngine The storage engine options JSON
     * @param comment The index creation comment
     * @param commitQuorum The createIndexes commit quorum
     */
    public record ResolvedIndex(@Nullable String name,
                                List<ResolvedIndexField> fields,
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
