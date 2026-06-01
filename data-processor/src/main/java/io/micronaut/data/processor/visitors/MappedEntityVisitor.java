/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.DataTransformer;
import io.micronaut.data.annotation.EmbeddedNaming;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.annotation.sql.ColumnTransformer;
import io.micronaut.data.annotation.sql.ETaggable;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;

import static io.micronaut.data.processor.visitors.Utils.getConfiguredDataConverters;
import static io.micronaut.data.processor.visitors.Utils.getConfiguredDataTypes;

/**
 * A {@link TypeElementVisitor} that pre-computes mappings to columns based on the configured naming strategy.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MappedEntityVisitor implements TypeElementVisitor<MappedEntity, Object> {
    /**
     * The position of the visitor.
     */
    public static final int POSITION = 100;

    private static final String JSON_VIEW_ANNOTATION = "io.micronaut.data.annotation.JsonView";
    private static final String JSON_SUB_VIEW_ANNOTATION = "io.micronaut.data.annotation.JsonSubView";
    private static final String JSON_PROPERTY_ANNOTATION = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String SERDE_CONFIG_ANNOTATION = "io.micronaut.serde.config.annotation.SerdeConfig";
    private static final String JSON_VIEW_ID = "_id";
    private static final String PROPERTY = "property";
    private static final String EMBEDDED_NAMING_STRATEGY = "micronaut.data.embedded.naming.strategy";
    private static final String LEGACY = "LEGACY";

    private final Map<String, SourcePersistentEntity> entityMap = new HashMap<>(50);
    private final Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<>() {
        @Override
        public SourcePersistentEntity apply(ClassElement classElement) {
            return entityMap.computeIfAbsent(classElement.getName(), s -> new SourcePersistentEntity(classElement, this));
        }
    };

    @Override
    public int getOrder() {
        // higher priority than the default
        return POSITION;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        SourcePersistentEntity entity = entityResolver.apply(element);
        Map<String, DataType> dataTypes = getConfiguredDataTypes(element);
        Map<String, String> dataConverters = getConfiguredDataConverters(element);
        boolean legacyEmbeddedNaming = isLegacyEmbeddedNaming(element, context);

        List<SourcePersistentProperty> properties = entity.getPersistentProperties();

        final List<AnnotationValue<Index>> indexes = properties.stream()
                .flatMap(prop -> prop.findAnnotation(Index.class).stream())
                .toList();

        if (!indexes.isEmpty()) {
           element.annotate(Indexes.class, builder -> builder.values(indexes.toArray(new AnnotationValue[]{})));
        }

        for (PersistentProperty property : properties) {
            computeMappingDefaults(property, dataTypes, dataConverters, context, legacyEmbeddedNaming);
        }
        if (entity.hasIdentity()) {
            SourcePersistentProperty identity = entity.getIdentity();
            computeMappingDefaults(identity, dataTypes, dataConverters, context, legacyEmbeddedNaming);
            if (entity.hasAnnotation(JSON_VIEW_ANNOTATION)) {
                handleJsonViewIdentity(identity);
            }
        }
        if (entity.hasCompositeIdentity()) {
            for (SourcePersistentProperty compositeIdentity : entity.getCompositeIdentity()) {
                computeMappingDefaults(compositeIdentity, dataTypes, dataConverters, context, legacyEmbeddedNaming);
            }
        }
        if (entity.hasVersion()) {
            computeMappingDefaults(entity.getVersion(), dataTypes, dataConverters, context, legacyEmbeddedNaming);
        }

        // Synthesize ColumnTransformer(read=...) for @GeneratedETag based on @ETagValue fields or implicit @ETaggable
        synthesizeETagColumnTransformer(entity, properties);

        if (entity.hasAnnotation(JSON_VIEW_ANNOTATION) || entity.hasAnnotation(JSON_SUB_VIEW_ANNOTATION)) {
            validateJsonView(entity, context);
        }
    }

    private void computeMappingDefaults(
            PersistentProperty property,
            Map<String, DataType> dataTypes,
            Map<String, String> dataConverters,
            VisitorContext context,
            boolean legacyEmbeddedNaming) {

        AnnotationMetadata annotationMetadata = property.getAnnotationMetadata();
        SourcePersistentProperty spp = (SourcePersistentProperty) property;
        PropertyElement propertyElement = spp.getPropertyElement();

        boolean isRelation = propertyElement.hasStereotype(Relation.class);

        DataType dataType = annotationMetadata.getValue(TypeDef.class, "type", DataType.class)
                .orElse(null);
        String converter = annotationMetadata.stringValue(MappedProperty.class, "converter")
                .orElseGet(() -> annotationMetadata.stringValue(TypeDef.class, "converter").orElse(null));
        if (Objects.equals(converter, Object.class.getName())) {
            converter = null;
        }
        if (converter == null) {
            ClassElement type = propertyElement.getGenericType();
            converter = TypeUtils.resolveDataConverter(type, dataConverters);
        }
        if (converter != null) {
            if (isRelation) {
                throw new ProcessingException(propertyElement, "Relation cannot have converter specified");
            }
            ClassElement persistedClassFromConverter = getPersistedClassFromConverter(converter, context);
            if (persistedClassFromConverter != null) {
                propertyElement.annotate(MappedProperty.class, builder -> {
                    builder.member("converterPersistedType", new AnnotationClassValue<>(persistedClassFromConverter.getCanonicalName()));
                });
            }
            if (dataType == null) {
                dataType = getDataTypeFromConverter(propertyElement.getGenericType(), converter, dataTypes, context);
                if (dataType == null) {
                    throw new ProcessingException(propertyElement, "Cannot recognize proper data type. Please use @TypeDef to specify one");
                }
            }
        } else {
            if (dataType == null && spp.getType().isEnum()) {
                if (spp.getOwner().getAnnotationMetadata().hasAnnotation("javax.persistence.Entity")
                        || spp.getOwner().getAnnotationMetadata().hasAnnotation("jakarta.persistence.Entity")) {
                    // JPA enums have default ORDINAL mapping for enums
                    dataType = DataType.INTEGER;
                }
            }

            if (dataType == null) {
                ClassElement type = propertyElement.getGenericType();
                dataType = TypeUtils.resolveDataType(type, dataTypes);
            }
        }

        if (dataType == DataType.ENTITY && !isRelation) {
            propertyElement = (PropertyElement) propertyElement.annotate(Relation.class, builder ->
                builder.value(Relation.Kind.MANY_TO_ONE)
            );
        } else if (isRelation) {
            Relation.Kind kind = propertyElement.enumValue(Relation.class, Relation.Kind.class).orElse(Relation.Kind.MANY_TO_ONE);
            if (kind == Relation.Kind.EMBEDDED || kind == Relation.Kind.MANY_TO_ONE) {
                if (propertyElement.stringValue(Relation.class, "mappedBy").isPresent()) {
                    throw new ProcessingException(propertyElement, "Relation " + kind + " doesn't support 'mappedBy'.");
                }
            }
            if (legacyEmbeddedNaming && kind == Relation.Kind.EMBEDDED) {
                propertyElement.annotate(EmbeddedNaming.class, builder -> builder.value(EmbeddedNaming.Strategy.LEGACY));
            }
        }

        if (dataType != DataType.OBJECT) {
            DataType finalDataType = dataType;
            propertyElement.annotate(MappedProperty.class, builder -> builder.member("type", finalDataType));
        }
        if (converter != null) {
            String finalConverter = converter;
            propertyElement.annotate(MappedProperty.class, builder -> builder.member("converter", new AnnotationClassValue<>(finalConverter)));
        }
        if (isRelation) {
            useJoinColumnNameIfSet(annotationMetadata, propertyElement);
        }
    }

    private boolean isLegacyEmbeddedNaming(ClassElement element, VisitorContext context) {
        Optional<String> configuredStrategy = Optional.ofNullable(context.getOptions().get(EMBEDDED_NAMING_STRATEGY))
            .or(() -> Optional.ofNullable(System.getProperty(EMBEDDED_NAMING_STRATEGY)));
        if (configuredStrategy.isEmpty()) {
            return false;
        }
        String strategy = configuredStrategy.get();
        if (LEGACY.equalsIgnoreCase(strategy)) {
            return true;
        }
        if ("STANDARD".equalsIgnoreCase(strategy)) {
            return false;
        }
        throw new ProcessingException(element, "Invalid value for '" + EMBEDDED_NAMING_STRATEGY + "': " + strategy + ". Supported values are LEGACY and STANDARD.");
    }

    /**
     * Validate JSON view or JSON subview entity definition.
     * The validation includes that if {@code @JsonView(entity=)} is specified, all defined
     * properties must exist on the corresponding entity class.
     *
     * @param entity The entity
     * @param context context
     */
    private void validateJsonView(SourcePersistentEntity entity, VisitorContext context) {
        String entityName = entity.stringValue(JsonView.class, "entity")
            .orElse(entity.stringValue(JsonSubView.class, "entity").orElse(null));

        if (entityName != null) {
            Optional<ClassElement> entityTypeOptional = context.getClassElement(entityName);
            if (entityTypeOptional.isPresent()) {
                ClassElement entityType = entityTypeOptional.get();
                if (entity.hasIdentity()) {
                    validateJsonViewProperty(entity.getIdentity(), entityType);
                }
                for (SourcePersistentProperty property : entity.getPersistentProperties()) {
                    validateJsonViewProperty(property, entityType);
                }
            }
        }
    }

    /**
     * Validates that a JSON view property corresponds to a property in the defined entity.
     * If a property has {@code @MappedProperty} or is embedded, the verification is skipped.
     *
     * @param property The property
     * @param entityType The type of the defined entity
     */
    private void validateJsonViewProperty(SourcePersistentProperty property, ClassElement entityType) {
        if (property.getDataType() == DataType.OBJECT
                || property.getAnnotationMetadata().stringValue(MappedProperty.class).isPresent()
                || (property instanceof Association association && association.getKind() == Relation.Kind.EMBEDDED)
        ) {
            return;
        }
        if (entityType.findField(property.getName()).isEmpty()) {
            throw new ProcessingException(property.getPropertyElement(), "Json View property " + property.getName() + " doesn't exist in the defined entity class " + entityType.getSimpleName());
        }
    }

    @Nullable
    private DataType getDataTypeFromConverter(ClassElement type, String converter, Map<String, DataType> dataTypes, VisitorContext context) {
        ClassElement classElement = context.getClassElement(converter).orElseThrow(IllegalStateException::new);
        ClassElement genericType = classElement.getGenericType();

        Map<String, ClassElement> typeArguments = genericType.getTypeArguments(AttributeConverter.class.getName());
        if (typeArguments.isEmpty()) {
            typeArguments = genericType.getTypeArguments("javax.persistence.AttributeConverter");
        }
        if (typeArguments.isEmpty()) {
            typeArguments = genericType.getTypeArguments("jakarta.persistence.AttributeConverter");
        }
        ClassElement entityElement = typeArguments.get("X");
        if (entityElement != null) {
            Optional<DataType> explicitType = entityElement.getValue(TypeDef.class, "type", DataType.class);
            if (explicitType.isPresent()) {
                return explicitType.get();
            }
        }
        Optional<DataType> explicitType = type.getValue(TypeDef.class, "type", DataType.class);
        if (explicitType.isPresent()) {
            return explicitType.get();
        }
        ClassElement dataTypeClassElement = typeArguments.get("Y");
        if (dataTypeClassElement != null) {
            DataType dataType = TypeUtils.resolveDataType(dataTypeClassElement, dataTypes);
            if (dataType == DataType.OBJECT) {
                dataType = null;
            }
            return dataType;
        }
        return null;
    }

    @Nullable
    private ClassElement getPersistedClassFromConverter(String converter, VisitorContext context) {
        ClassElement classElement = context.getClassElement(converter).orElseThrow(IllegalStateException::new);
        ClassElement genericType = classElement.getGenericType();

        Map<String, ClassElement> typeArguments = genericType.getTypeArguments(AttributeConverter.class.getName());
        if (typeArguments.isEmpty()) {
            typeArguments = genericType.getTypeArguments("javax.persistence.AttributeConverter");
        }
        if (typeArguments.isEmpty()) {
            typeArguments = genericType.getTypeArguments("jakarta.persistence.AttributeConverter");
        }
        return typeArguments.get("Y");
    }

    /**
     * If property is association and has JoinColumn annotation, we want to use MappedProperty from JoinColumn name
     * or else query builder will attempt to join with association id which might not be correct join column.
     *
     * @param annotationMetadata the annotation metadata
     * @param propertyElement the property element
     */
    private void useJoinColumnNameIfSet(AnnotationMetadata annotationMetadata, PropertyElement propertyElement) {
        String mappedPropertyValue = annotationMetadata.stringValue(MappedProperty.class, AnnotationMetadata.VALUE_MEMBER).orElse(null);
        // We do this only if MappedProperty value does not have explicitly set value
        if (mappedPropertyValue != null) {
            return;
        }
        AnnotationValue<JoinColumns> joinColumnsAnnotationValue = annotationMetadata.getAnnotation(JoinColumns.class);
        // and if JoinColumn is set
        if (joinColumnsAnnotationValue == null) {
            return;
        }
        List<AnnotationValue<JoinColumn>> joinColumnsAnnotationValueAnnotations = joinColumnsAnnotationValue.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
        if (joinColumnsAnnotationValueAnnotations.size() != 1) {
            // Set MappedProperty value only if just one JoinColumn configured
            return;
        }
        AnnotationValue<JoinColumn> joinColumnAnnotationValue = joinColumnsAnnotationValueAnnotations.get(0);
        String joinColumnName = joinColumnAnnotationValue.stringValue("name").orElse(null);
        if (joinColumnName != null) {
            propertyElement.annotate(MappedProperty.class, builder -> builder.member(AnnotationMetadata.VALUE_MEMBER, joinColumnName));
        }
    }

    /**
     * An identity field for Oracle duality Json View has to be '_id' so we are verifying and configuring it here.
     *
     * @param identity the identity field
     */
    private void handleJsonViewIdentity(SourcePersistentProperty identity) {
        PropertyElement identityPropertyElement = identity.getPropertyElement();
        String jsonPropertyIdName = identity.stringValue(JSON_PROPERTY_ANNOTATION).orElse(null);
        if (jsonPropertyIdName != null && !jsonPropertyIdName.equals(JSON_VIEW_ID)) {
            throw new ProcessingException(identity, "@JsonView identity @JsonProperty value cannot be set to value different than '" + JSON_VIEW_ID + "'");
        }
        String serdeConfigPropertyIdName = identity.stringValue(SERDE_CONFIG_ANNOTATION, PROPERTY).orElse(null);
        if (serdeConfigPropertyIdName == null) {
            identityPropertyElement.annotate(SERDE_CONFIG_ANNOTATION, builder -> builder.member(PROPERTY, JSON_VIEW_ID));
        } else if (!serdeConfigPropertyIdName.equals(JSON_VIEW_ID)) {
            throw new ProcessingException(identity, "@JsonView identity @SerdeConfig property cannot be set to value different than '" + JSON_VIEW_ID + "'");
        }
    }

    private void synthesizeETagColumnTransformer(SourcePersistentEntity entity,
                                                 List<SourcePersistentProperty> properties) {
        validateGeneratedETagDeclarations(entity);
        List<SourcePersistentProperty> allProperties = allProperties(entity, properties);
        // Find the property annotated with @GeneratedETag (the ETag holder)
        List<SourcePersistentProperty> etagPropList = allProperties.stream()
            .filter(p -> p.getAnnotationMetadata().hasAnnotation(GeneratedETag.class))
            .toList();
        if (CollectionUtils.isEmpty(etagPropList)) {
            return;
        }
        if (etagPropList.size() > 1) {
            throw new ProcessingException(etagPropList.get(1), "Only one field can be marked as @GeneratedETag");
        }
        SourcePersistentProperty etagProp = etagPropList.get(0);
        if (entity.getIdentityProperties().contains(etagProp)) {
            throw new ProcessingException(etagProp, "@GeneratedETag cannot be applied to an @Id property");
        }
        if (entity.hasVersion() && !Objects.equals(entity.getVersion(), etagProp)) {
            throw new ProcessingException(etagProp, "Entity with @Version field cannot have @GeneratedETag field");
        }
        if (!String.class.getName().equals(etagProp.getTypeName())) {
            throw new ProcessingException(etagProp, "@GeneratedETag property must be a String");
        }
        validateNoTransientETagValues(entity);

        AnnotationMetadata etagMetadata = etagProp.getAnnotationMetadata();
        String function = etagMetadata.stringValue(GeneratedETag.class, "function").orElse("");
        if (function.isEmpty()) {
            function = GeneratedETag.DIALECT_DEFAULT_FUNCTION_MARKER;
        }

        boolean entityETaggable = entity.getType().hasStereotype(ETaggable.class);
        boolean includeForeignKeys = entity.getType().booleanValue(ETaggable.class, "includeForeignKeys").orElse(false);
        Set<String> parts = new LinkedHashSet<>();

        // Traverse all persistent properties (including identity) and collect @ETagValue columns or implicitly included ones.
        // This handles embedded paths and association FKs consistently with the rest of the project.
        PersistentEntityUtils.traversePersistentProperties(entity, true, false, (associations, property) -> {
            if (hasNonEmbeddedAssociationPath(associations)) {
                return;
            }
            boolean excludedByAssociationPath = associations.stream()
                .anyMatch(association -> association.getAnnotationMetadata().booleanValue(ETagValue.class, "exclude").orElse(false));
            if (excludedByAssociationPath) {
                return;
            }
            AnnotationMetadata metadata = property.getAnnotationMetadata();
            boolean excluded = metadata.booleanValue(ETagValue.class, "exclude").orElse(false);
            if (excluded) {
                return;
            }
            boolean explicit = metadata.hasAnnotation(ETagValue.class);
            boolean explicitByEmbedded = isIncludedByEmbeddedAssociationPath(associations);
            boolean eligible = ImplicitEtagUtils.isImplicitEtagEligible(entity, associations, property, etagProp, includeForeignKeys);
            if (explicit && !eligible) {
                throw new ProcessingException(((SourcePersistentProperty) property).getPropertyElement(),
                    "Explicit @ETagValue cannot be applied to ineligible property: " + property.getName());
            }
            boolean implicit = entityETaggable && eligible;
            if (eligible && (explicit || explicitByEmbedded || implicit)) {
                String column = entity.getNamingStrategy().mappedName(associations, property);
                parts.add(column);
            }
        });

        // Implicit include for owning-side FKs that traversal skips
        if (entityETaggable && includeForeignKeys) {
            for (SourcePersistentProperty p : allProperties) {
                AnnotationMetadata metadata = p.getAnnotationMetadata();
                boolean excluded = metadata.booleanValue(ETagValue.class, "exclude").orElse(false);
                if (excluded) {
                    continue;
                }
                if (p instanceof io.micronaut.data.model.Association assoc && !(assoc instanceof io.micronaut.data.model.Embedded)) {
                    if (isOwningForeignKeyAssociation(assoc)) {
                        addOwningForeignKeyColumns(entity, assoc, parts);
                    }
                }
            }
        }

        // Handle explicit @ETagValue placed on owning-side FK associations which are skipped by traversal
        for (SourcePersistentProperty p : allProperties) {
            AnnotationMetadata metadata = p.getAnnotationMetadata();
            boolean excluded = metadata.booleanValue(ETagValue.class, "exclude").orElse(false);
            if (excluded) {
                continue;
            }
            if (metadata.hasAnnotation(ETagValue.class)) {
                // If explicit on a relation, validate it's either embedded or an owning-side FK
                if (metadata.hasAnnotation(io.micronaut.data.annotation.Relation.class)) {
                    var kind = p.enumValue(io.micronaut.data.annotation.Relation.class, "value", io.micronaut.data.annotation.Relation.Kind.class).orElse(null);
                    if (kind == io.micronaut.data.annotation.Relation.Kind.ONE_TO_MANY || kind == io.micronaut.data.annotation.Relation.Kind.MANY_TO_MANY) {
                        throw new ProcessingException(p, "Explicit @ETagValue on non-embedded, non-foreign-key association is not supported");
                    }
                }
                if (p instanceof io.micronaut.data.model.Association assoc) {
                    if (assoc instanceof io.micronaut.data.model.Embedded) {
                        continue;
                    }
                    if (isOwningForeignKeyAssociation(assoc)) {
                        addOwningForeignKeyColumns(entity, assoc, parts);
                    } else {
                        throw new ProcessingException(p, "Explicit @ETagValue on non-embedded, non-foreign-key association is not supported");
                    }
                }
            }
        }

        if (parts.isEmpty()) {
            throw new ProcessingException(etagProp, "@GeneratedETag requires at least one @ETagValue annotated field or @ETaggable on the entity");
        }
        // Ensure @Version and @GeneratedValue are present on the ETag property
        PropertyElement etagPropertyElement = etagProp.getPropertyElement();
        etagPropertyElement.annotate(Version.class, b -> { });
        etagPropertyElement.annotate(GeneratedValue.class, b -> { });
        String expr = buildEtagReadExpression(function, parts);
        // Apply both ColumnTransformer and aliased DataTransformer for downstream consumers/tests
        etagPropertyElement.annotate(ColumnTransformer.class, builder -> builder.member("read", expr));
        etagPropertyElement.annotate(DataTransformer.class, builder -> builder.member("read", expr));
        // Also annotate the read accessor to ensure javac-backed tests observe the synthesized transformer
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
        return associations.stream().anyMatch(association -> !(association instanceof io.micronaut.data.model.Embedded));
    }

    private static boolean isIncludedByEmbeddedAssociationPath(List<Association> associations) {
        return associations.stream()
            .filter(association -> association instanceof io.micronaut.data.model.Embedded)
            .anyMatch(association -> association.getAnnotationMetadata().hasAnnotation(ETagValue.class)
                && !association.getAnnotationMetadata().booleanValue(ETagValue.class, "exclude").orElse(false));
    }

    private static boolean isOwningForeignKeyAssociation(Association association) {
        Relation.Kind kind = association.getKind();
        return (kind == Relation.Kind.MANY_TO_ONE || kind == Relation.Kind.ONE_TO_ONE)
            && association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").isEmpty();
    }

    private static void addOwningForeignKeyColumns(SourcePersistentEntity entity,
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

    private static List<String> explicitJoinColumnNames(Association association) {
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
                && !propertyElement.booleanValue(ETagValue.class, "exclude").orElse(false)
                && propertyElement.hasStereotype(Transient.class)) {
                throw new ProcessingException(propertyElement, "Explicit @ETagValue cannot be applied to transient property: " + propertyElement.getName());
            }
        }
    }
}
