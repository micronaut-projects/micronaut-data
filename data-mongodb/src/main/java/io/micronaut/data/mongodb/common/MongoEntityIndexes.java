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
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex;
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField;
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection;
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed;
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexType;
import io.micronaut.data.mongodb.annotation.index.MongoHashedIndexed;
import io.micronaut.data.mongodb.annotation.index.MongoIndexed;
import io.micronaut.data.mongodb.annotation.index.MongoTextIndexed;
import io.micronaut.data.mongodb.annotation.index.MongoWildcardIndex;
import io.micronaut.data.mongodb.annotation.index.MongoWildcardIndexed;
import io.micronaut.data.mongodb.geo.MongoGeoConverters;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Map<WildcardIndexSignature, List<ResolvedIndex>> groupedIndexes = new LinkedHashMap<>();
        for (var annotation : entity.getAnnotationMetadata().getAnnotationValuesByType(MongoWildcardIndex.class)) {
            ResolvedIndex index = new ResolvedIndex(
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
            );
            groupedIndexes.computeIfAbsent(new WildcardIndexSignature(index), ignored -> new ArrayList<>()).add(index);
        }
        if (groupedIndexes.isEmpty()) {
            return List.of();
        }
        List<ResolvedIndex> resolvedIndexes = new ArrayList<>(groupedIndexes.size());
        for (List<ResolvedIndex> indexes : groupedIndexes.values()) {
            ResolvedIndex first = indexes.getFirst();
            String mergedName = first.name();
            for (int i = 1; i < indexes.size(); i++) {
                ResolvedIndex candidate = indexes.get(i);
                if (mergedName == null) {
                    mergedName = candidate.name();
                } else if (candidate.name() != null && !mergedName.equals(candidate.name())) {
                    throw new IllegalStateException("Mongo top-level wildcard indexes on entity [" + entity.getName() + "] must use the same index name when declaring equivalent key [$**]");
                }
            }
            resolvedIndexes.add(new ResolvedIndex(
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
        return List.copyOf(resolvedIndexes);
    }

    private static List<ResolvedIndex> resolveFieldIndexes(RuntimePersistentEntity<?> entity) {
        List<ResolvedIndex> indexes = new ArrayList<>();
        PersistentEntityUtils.traversePersistentProperties(entity, false, false, (associations, property) -> {
            if (!isIndexableField(property, associations)) {
                return;
            }
            RuntimePersistentProperty<?> runtimeProperty = (RuntimePersistentProperty<?>) property;
            String persistedPath = toPersistedPath(associations, property);
            var annotationMetadata = property.getAnnotationMetadata();
            var annotation = annotationMetadata.getAnnotation(MongoIndexed.class);
            if (annotation != null) {
                indexes.add(new ResolvedIndex(
                        annotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                        List.of(new ResolvedIndexField(persistedPath, annotation.enumValue("direction", MongoIndexDirection.class).orElse(MongoIndexDirection.ASC) == MongoIndexDirection.DESC ? -1 : 1, null, null, null, null)),
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
                return;
            }
            var textAnnotation = annotationMetadata.getAnnotation(MongoTextIndexed.class);
            if (textAnnotation != null) {
                return;
            }
            var hashedAnnotation = annotationMetadata.getAnnotation(MongoHashedIndexed.class);
            if (hashedAnnotation != null) {
                indexes.add(new ResolvedIndex(
                        hashedAnnotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                        List.of(new ResolvedIndexField(persistedPath, null, null, "hashed", null, null)),
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
                return;
            }
            var geoAnnotation = annotationMetadata.getAnnotation(MongoGeoIndexed.class);
            if (geoAnnotation != null) {
                validateGeoIndexedType(entity, runtimeProperty);
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
                        List.of(new ResolvedIndexField(persistedPath, null, null, type.getKey(), min, max)),
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
                return;
            }
            var wildcardAnnotation = annotationMetadata.getAnnotation(MongoWildcardIndexed.class);
            if (wildcardAnnotation != null) {
                String wildcardProjection = wildcardAnnotation.stringValue("wildcardProjection").filter(s -> !s.isEmpty()).orElse(null);
                if (wildcardProjection != null) {
                    throw new IllegalStateException("Mongo wildcardProjection on field-level @MongoWildcardIndexed is not supported by MongoDB. Use @MongoWildcardIndex on the entity instead for top-level wildcard projection.");
                }
                indexes.add(new ResolvedIndex(
                        wildcardAnnotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null),
                        List.of(new ResolvedIndexField(persistedPath + ".$**", 1, null, null, null, null)),
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
        });
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
        TextIndexState state = new TextIndexState();
        PersistentEntityUtils.traversePersistentProperties(entity, false, false, (associations, property) -> {
            if (!isIndexableField(property, associations)) {
                return;
            }
            var textAnnotation = property.getAnnotationMetadata().getAnnotation(MongoTextIndexed.class);
            if (textAnnotation == null) {
                return;
            }
            int weight = textAnnotation.intValue("weight").orElse(1);
            if (weight <= 0) {
                throw new IllegalStateException("Mongo text index weight must be greater than zero for entity [" + entity.getName() + "]");
            }
            String declaredName = textAnnotation.stringValue("name").filter(s -> !s.isEmpty()).orElse(null);
            if (state.name == null) {
                state.name = declaredName;
            } else if (declaredName != null && !declaredName.equals(state.name)) {
                throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same index name");
            }
            boolean declaredHidden = textAnnotation.booleanValue("hidden").orElse(false);
            if (state.hidden == null) {
                state.hidden = declaredHidden;
            } else if (!state.hidden.equals(declaredHidden)) {
                throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same hidden option");
            }
            String declaredComment = textAnnotation.stringValue("comment").filter(s -> !s.isEmpty()).orElse(null);
            if (state.comment == null) {
                state.comment = declaredComment;
            } else if (!Objects.equals(state.comment, declaredComment)) {
                throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same comment option");
            }
            String declaredStorageEngine = parseJsonOption(textAnnotation.stringValue("storageEngine").filter(s -> !s.isEmpty()).orElse(null), "storageEngine", entity.getName());
            if (state.storageEngine == null) {
                state.storageEngine = declaredStorageEngine;
            } else if (!Objects.equals(state.storageEngine, declaredStorageEngine)) {
                throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same storageEngine option");
            }
            String declaredDefaultLanguage = textAnnotation.stringValue("defaultLanguage").filter(s -> !s.isEmpty()).orElse(null);
            if (state.defaultLanguage == null) {
                state.defaultLanguage = declaredDefaultLanguage;
            } else if (!Objects.equals(state.defaultLanguage, declaredDefaultLanguage)) {
                throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same defaultLanguage option");
            }
            String declaredLanguageOverride = textAnnotation.stringValue("languageOverride").filter(s -> !s.isEmpty()).orElse(null);
            if (state.languageOverride == null) {
                state.languageOverride = declaredLanguageOverride;
            } else if (!Objects.equals(state.languageOverride, declaredLanguageOverride)) {
                throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same languageOverride option");
            }
            Integer declaredTextIndexVersion = textAnnotation.intValue("textIndexVersion").isPresent() && textAnnotation.intValue("textIndexVersion").getAsInt() >= 0
                    ? textAnnotation.intValue("textIndexVersion").getAsInt() : null;
            if (declaredTextIndexVersion != null && declaredTextIndexVersion <= 0) {
                throw new IllegalStateException("Mongo text index version must be greater than zero for entity [" + entity.getName() + "]");
            }
            if (state.textIndexVersion == null) {
                state.textIndexVersion = declaredTextIndexVersion;
            } else if (!Objects.equals(state.textIndexVersion, declaredTextIndexVersion)) {
                throw new IllegalStateException("Mongo text indexed fields on entity [" + entity.getName() + "] must use the same textIndexVersion option");
            }
            state.fields.add(new ResolvedIndexField(toPersistedPath(associations, property), null, weight, "text", null, null));
        });
        if (state.fields.isEmpty()) {
            return List.of();
        }
        return List.of(new ResolvedIndex(state.name, List.copyOf(state.fields), false, false, state.hidden != null && state.hidden, null, null, null, null, null, null, state.defaultLanguage, state.languageOverride, state.textIndexVersion, null, null, state.storageEngine, state.comment, null));
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
                String pathForLookup = path.contains(".") ? path.replace('.', '_') : path;
                String persistedPath = PersistentEntityUtils.getPersistentPropertyPath(entity, pathForLookup)
                        .map(persistentPath -> {
                            var propertyPath = entity.getPropertyPath(persistentPath);
                            if (propertyPath == null) {
                                throw new IllegalStateException("Invalid Mongo index path [" + path + "] for entity [" + entity.getName() + "]");
                            }
                            return toPersistedPath(propertyPath);
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

    private static boolean isIndexableField(PersistentProperty property, List<Association> associations) {
        return property instanceof RuntimePersistentProperty<?> && !containsNonEmbeddedAssociation(associations);
    }

    private static boolean containsNonEmbeddedAssociation(List<Association> associations) {
        for (Association association : associations) {
            if (!(association instanceof io.micronaut.data.model.Embedded)) {
                return true;
            }
        }
        return false;
    }

    private static String toPersistedPath(List<Association> associations, PersistentProperty property) {
        StringBuilder resolved = new StringBuilder();
        for (Association association : associations) {
            if (!resolved.isEmpty()) {
                resolved.append('.');
            }
            resolved.append(association.getPersistedName());
        }
        if (!resolved.isEmpty()) {
            resolved.append('.');
        }
        resolved.append(property.getPersistedName());
        return resolved.toString();
    }

    private static String toPersistedPath(PersistentPropertyPath propertyPath) {
        return toPersistedPath(propertyPath.getAssociations(), propertyPath.getProperty());
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

    private static final class TextIndexState {
        private final List<ResolvedIndexField> fields = new ArrayList<>();
        private @Nullable String name;
        private @Nullable Boolean hidden;
        private @Nullable String storageEngine;
        private @Nullable String comment;
        private @Nullable String defaultLanguage;
        private @Nullable String languageOverride;
        private @Nullable Integer textIndexVersion;
    }

    private record WildcardIndexSignature(List<ResolvedIndexField> fields,
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

        private WildcardIndexSignature(ResolvedIndex index) {
            this(index.fields(),
                    index.unique(),
                    index.sparse(),
                    index.hidden(),
                    index.expireAfterSeconds(),
                    index.partialFilterExpression(),
                    index.collation(),
                    index.bits(),
                    index.min(),
                    index.max(),
                    index.defaultLanguage(),
                    index.languageOverride(),
                    index.textIndexVersion(),
                    index.sphereVersion(),
                    index.wildcardProjection(),
                    index.storageEngine(),
                    index.comment(),
                    index.commitQuorum());
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
