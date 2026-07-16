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
package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.DataTransformer;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.annotation.sql.ColumnTransformer;
import io.micronaut.data.annotation.sql.ETaggable;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Utilities for validating {@link GeneratedETag} declarations and synthesizing
 * the generated ETag read transformer metadata during entity processing.
 */
@Internal
final class GeneratedETagUtils {

    private static final String EXCLUDE = "exclude";

    private GeneratedETagUtils() {
    }

    /**
     * Validates generated ETag mapping annotations and applies the computed
     * {@link ColumnTransformer} and {@link DataTransformer} read expressions to
     * the generated ETag property.
     *
     * @param entity The source persistent entity
     * @param properties The persistent properties collected for the entity
     */
    static void synthesizeColumnTransformer(SourcePersistentEntity entity,
                                            List<SourcePersistentProperty> properties) {
        validateGeneratedETagDeclarations(entity);
        List<SourcePersistentProperty> allProperties = allProperties(entity, properties);
        SourcePersistentProperty etagProp = findGeneratedETagProperty(allProperties);
        if (etagProp == null) {
            return;
        }
        validateGeneratedETagProperty(entity, etagProp);
        validateNoTransientETagValues(entity);

        Set<String> parts = collectETagParts(entity, allProperties, etagProp);
        if (parts.isEmpty()) {
            throw new ProcessingException(etagProp, "@GeneratedETag requires at least one @ETagValue annotated field or @ETaggable on the entity");
        }

        String expr = buildEtagReadExpression(generatedETagFunction(etagProp), parts);
        applyGeneratedETagTransformer(entity, etagProp, expr);
    }

    private static @Nullable SourcePersistentProperty findGeneratedETagProperty(List<SourcePersistentProperty> properties) {
        List<SourcePersistentProperty> etagPropList = properties.stream()
            .filter(p -> p.getAnnotationMetadata().hasAnnotation(GeneratedETag.class))
            .toList();
        if (CollectionUtils.isEmpty(etagPropList)) {
            return null;
        }
        if (etagPropList.size() > 1) {
            throw new ProcessingException(etagPropList.get(1), "Only one field can be marked as @GeneratedETag");
        }
        return etagPropList.get(0);
    }

    private static void validateGeneratedETagProperty(SourcePersistentEntity entity,
                                                      SourcePersistentProperty etagProp) {
        if (entity.getIdentityProperties().contains(etagProp)) {
            throw new ProcessingException(etagProp, "@GeneratedETag cannot be applied to an @Id property");
        }
        if (entity.hasVersion() && !Objects.equals(entity.getVersion(), etagProp)) {
            throw new ProcessingException(etagProp, "Entity with @Version field cannot have @GeneratedETag field");
        }
        if (!String.class.getName().equals(etagProp.getTypeName())) {
            throw new ProcessingException(etagProp, "@GeneratedETag property must be a String");
        }
    }

    private static String generatedETagFunction(SourcePersistentProperty etagProp) {
        AnnotationMetadata etagMetadata = etagProp.getAnnotationMetadata();
        String function = etagMetadata.stringValue(GeneratedETag.class, "function").orElse("");
        if (function.isEmpty()) {
            return GeneratedETag.DIALECT_DEFAULT_FUNCTION_MARKER;
        }
        return function;
    }

    private static Set<String> collectETagParts(SourcePersistentEntity entity,
                                                List<SourcePersistentProperty> allProperties,
                                                SourcePersistentProperty etagProp) {
        boolean entityETaggable = entity.getType().hasStereotype(ETaggable.class);
        boolean includeForeignKeys = entity.getType().booleanValue(ETaggable.class, "includeForeignKeys").orElse(false);
        Set<String> parts = new LinkedHashSet<>();
        collectTraversedETagColumns(entity, etagProp, entityETaggable, includeForeignKeys, parts);
        collectImplicitForeignKeyColumns(entity, allProperties, entityETaggable, includeForeignKeys, parts);
        collectExplicitAssociationColumns(entity, allProperties, parts);
        return parts;
    }

    private static void collectTraversedETagColumns(SourcePersistentEntity entity,
                                                    SourcePersistentProperty etagProp,
                                                    boolean entityETaggable,
                                                    boolean includeForeignKeys,
                                                    Set<String> parts) {
        PersistentEntityUtils.traversePersistentProperties(entity, true, false, (associations, property) -> {
            boolean eligible = ImplicitETagUtils.isImplicitEtagEligible(entity, associations, property, etagProp, includeForeignKeys);
            if (shouldAddTraversedColumn(entityETaggable, associations, property, eligible)) {
                String column = entity.getNamingStrategy().mappedName(associations, property);
                parts.add(column);
            }
        });
    }

    private static boolean shouldAddTraversedColumn(boolean entityETaggable,
                                                    List<Association> associations,
                                                    PersistentProperty property,
                                                    boolean eligible) {
        if (hasNonEmbeddedAssociationPath(associations) || isExcludedByETagValue(associations) || isExcludedByETagValue(property)) {
            return false;
        }
        boolean explicit = property.getAnnotationMetadata().hasAnnotation(ETagValue.class);
        if (explicit && !eligible) {
            throw new ProcessingException(((SourcePersistentProperty) property).getPropertyElement(),
                "Explicit @ETagValue cannot be applied to ineligible property: " + property.getName());
        }
        return eligible && (explicit || isIncludedByEmbeddedAssociationPath(associations) || entityETaggable);
    }

    private static void collectImplicitForeignKeyColumns(SourcePersistentEntity entity,
                                                         List<SourcePersistentProperty> allProperties,
                                                         boolean entityETaggable,
                                                         boolean includeForeignKeys,
                                                         Set<String> parts) {
        if (!entityETaggable || !includeForeignKeys) {
            return;
        }
        for (SourcePersistentProperty property : allProperties) {
            if (isExcludedByETagValue(property)) {
                continue;
            }
            if (property instanceof Association association
                && !(association instanceof Embedded)
                && isOwningForeignKeyAssociation(association)) {
                addOwningForeignKeyColumns(entity, association, parts);
            }
        }
    }

    private static void collectExplicitAssociationColumns(SourcePersistentEntity entity,
                                                          List<SourcePersistentProperty> allProperties,
                                                          Set<String> parts) {
        for (SourcePersistentProperty property : allProperties) {
            AnnotationMetadata metadata = property.getAnnotationMetadata();
            if (isExcludedByETagValue(property) || !metadata.hasAnnotation(ETagValue.class)) {
                continue;
            }
            if (metadata.hasAnnotation(Relation.class)) {
                var kind = property.enumValue(Relation.class, "value", Relation.Kind.class).orElse(null);
                if (kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY) {
                    throw new ProcessingException(property, "Explicit @ETagValue on non-embedded, non-foreign-key association is not supported");
                }
            }
            collectExplicitAssociationColumn(entity, property, parts);
        }
    }

    private static void collectExplicitAssociationColumn(SourcePersistentEntity entity,
                                                         SourcePersistentProperty property,
                                                         Set<String> parts) {
        if (!(property instanceof Association association) || association instanceof Embedded) {
            return;
        }
        if (isOwningForeignKeyAssociation(association)) {
            addOwningForeignKeyColumns(entity, association, parts);
            return;
        }
        throw new ProcessingException(property, "Explicit @ETagValue on non-embedded, non-foreign-key association is not supported");
    }

    private static void applyGeneratedETagTransformer(SourcePersistentEntity entity,
                                                      SourcePersistentProperty etagProp,
                                                      String expr) {
        PropertyElement etagPropertyElement = etagProp.getPropertyElement();
        validateNoConflictingTransformer(entity, etagProp, expr);
        etagPropertyElement.annotate(Version.class, b -> { });
        etagPropertyElement.annotate(GeneratedValue.class, b -> { });
        etagPropertyElement.annotate(ColumnTransformer.class, builder -> builder.member("read", expr));
        etagPropertyElement.annotate(DataTransformer.class, builder -> builder.member("read", expr));
        etagPropertyElement.getReadMethod().ifPresent(m -> {
            m.annotate(ColumnTransformer.class, b -> b.member("read", expr));
            m.annotate(DataTransformer.class, b -> b.member("read", expr));
        });
    }

    private static void validateGeneratedETagDeclarations(SourcePersistentEntity entity) {
        List<PropertyElement> generatedETagProperties = entity.getClassElement().getBeanProperties().stream()
            .filter(propertyElement -> propertyElement.hasAnnotation(GeneratedETag.class))
            .toList();
        if (generatedETagProperties.size() > 1) {
            throw new ProcessingException(generatedETagProperties.get(1), "Only one field can be marked as @GeneratedETag");
        }
        if (!generatedETagProperties.isEmpty()) {
            PropertyElement propertyElement = generatedETagProperties.get(0);
            if (propertyElement.hasStereotype(Transient.class)) {
                throw new ProcessingException(propertyElement, "@GeneratedETag cannot be applied to a @Transient property: " + propertyElement.getName());
            }
        }
    }

    private static String buildEtagReadExpression(String function, Set<String> parts) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String p : parts) {
            joiner.add("@." + p);
        }
        return function + "(" + joiner + ")";
    }

    private static void validateNoConflictingTransformer(SourcePersistentEntity entity,
                                                         SourcePersistentProperty etagProp,
                                                         String expr) {
        PropertyElement propertyElement = etagProp.getPropertyElement();
        if (!propertyElement.hasAnnotation(ColumnTransformer.class) && !propertyElement.hasAnnotation(DataTransformer.class)) {
            return;
        }
        if (isSynthesizedGeneratedETagTransformer(etagProp, expr)) {
            return;
        }
        throw new ProcessingException(entity, "@GeneratedETag cannot be combined with @ColumnTransformer or @DataTransformer on entity "
            + entity.getName() + ": " + propertyElement.getName());
    }

    private static boolean isSynthesizedGeneratedETagTransformer(SourcePersistentProperty etagProp, String expr) {
        AnnotationMetadata metadata = etagProp.getAnnotationMetadata();
        return metadata.hasAnnotation(Version.class)
            && metadata.hasAnnotation(GeneratedValue.class)
            && readTransformerMatches(metadata, ColumnTransformer.class, expr)
            && readTransformerMatches(metadata, DataTransformer.class, expr)
            && writeTransformerEmpty(metadata, ColumnTransformer.class)
            && writeTransformerEmpty(metadata, DataTransformer.class);
    }

    private static boolean readTransformerMatches(AnnotationMetadata metadata,
                                                  Class<?> annotationType,
                                                  String expr) {
        return metadata.stringValue(annotationType.getName(), "read")
            .map(expr::equals)
            .orElse(false);
    }

    private static boolean writeTransformerEmpty(AnnotationMetadata metadata,
                                                 Class<?> annotationType) {
        return metadata.stringValue(annotationType.getName(), "write")
            .map(String::isEmpty)
            .orElse(true);
    }

    private static List<SourcePersistentProperty> allProperties(SourcePersistentEntity entity,
                                                                List<SourcePersistentProperty> properties) {
        List<SourcePersistentProperty> allProperties = new ArrayList<>(properties);
        for (PersistentProperty identityProperty : entity.getIdentityProperties()) {
            SourcePersistentProperty sourceIdentityProperty = (SourcePersistentProperty) identityProperty;
            if (!allProperties.contains(sourceIdentityProperty)) {
                allProperties.add(sourceIdentityProperty);
            }
        }
        if (entity.hasVersion() && !allProperties.contains(entity.getVersion())) {
            allProperties.add(entity.getVersion());
        }
        return allProperties;
    }

    private static boolean hasNonEmbeddedAssociationPath(List<Association> associations) {
        return associations.stream().anyMatch(association -> !(association instanceof Embedded));
    }

    private static boolean isExcludedByETagValue(List<Association> associations) {
        return associations.stream().anyMatch(GeneratedETagUtils::isExcludedByETagValue);
    }

    private static boolean isExcludedByETagValue(PersistentProperty property) {
        return property.getAnnotationMetadata().booleanValue(ETagValue.class, EXCLUDE).orElse(false);
    }

    private static boolean isIncludedByEmbeddedAssociationPath(List<Association> associations) {
        return associations.stream()
            .filter(Embedded.class::isInstance)
            .anyMatch(association -> association.getAnnotationMetadata().hasAnnotation(ETagValue.class)
                && !association.getAnnotationMetadata().booleanValue(ETagValue.class, EXCLUDE).orElse(false));
    }

    private static boolean isOwningForeignKeyAssociation(Association association) {
        Relation.Kind kind = association.getKind();
        return (kind == Relation.Kind.MANY_TO_ONE || kind == Relation.Kind.ONE_TO_ONE)
            && association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").isEmpty();
    }

    /**
     * Adds the physical column names for an owning foreign-key association.
     *
     * @param entity The entity that owns the generated ETag
     * @param association The owning association to inspect
     * @param parts The ordered ETag column names being accumulated
     */
    static void addOwningForeignKeyColumns(SourcePersistentEntity entity,
                                           Association association,
                                           Set<String> parts) {
        List<String> explicitJoinColumnNames = explicitJoinColumnNames(association);
        if (!explicitJoinColumnNames.isEmpty()) {
            parts.addAll(explicitJoinColumnNames);
            return;
        }
        for (PersistentProperty identityProperty : association.getAssociatedEntity().getIdentityProperties()) {
            PersistentEntityUtils.traversePersistentProperties(List.of(association), identityProperty, (associations, property) -> {
                String column = entity.getNamingStrategy().mappedName(associations, property);
                parts.add(column);
            });
        }
    }

    /**
     * Resolves explicitly declared join-column names for an association.
     *
     * @param association The association to inspect
     * @return The explicit join-column names, or an empty list when any name is missing
     */
    static List<String> explicitJoinColumnNames(Association association) {
        AnnotationValue<JoinColumns> joinColumnsAnnotationValue = association.getAnnotationMetadata().getAnnotation(JoinColumns.class);
        if (joinColumnsAnnotationValue == null) {
            return List.of();
        }
        List<AnnotationValue<JoinColumn>> joinColumnAnnotations = joinColumnsAnnotationValue.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
        if (CollectionUtils.isEmpty(joinColumnAnnotations)) {
            return List.of();
        }
        List<String> joinColumnNames = new ArrayList<>(joinColumnAnnotations.size());
        for (AnnotationValue<JoinColumn> joinColumnAnnotation : joinColumnAnnotations) {
            String name = joinColumnAnnotation.stringValue("name").orElse("");
            if (name.isEmpty()) {
                return List.of();
            }
            joinColumnNames.add(name);
        }
        return joinColumnNames;
    }

    private static void validateNoTransientETagValues(SourcePersistentEntity entity) {
        for (PropertyElement propertyElement : entity.getClassElement().getBeanProperties()) {
            if (propertyElement.hasAnnotation(ETagValue.class)
                && !propertyElement.booleanValue(ETagValue.class, EXCLUDE).orElse(false)
                && propertyElement.hasStereotype(Transient.class)) {
                throw new ProcessingException(propertyElement, "Explicit @ETagValue cannot be applied to transient property: " + propertyElement.getName());
            }
        }
    }
}
