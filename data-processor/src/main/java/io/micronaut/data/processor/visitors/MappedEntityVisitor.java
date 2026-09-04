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
import io.micronaut.data.annotation.EmbeddedNaming;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.InstantiateWithDefaultConstructor;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private static final String JSON_CREATOR_ANNOTATION = "com.fasterxml.jackson.annotation.JsonCreator";
    private static final String JACKSON3_JSON_CREATOR_ANNOTATION = "tools.jackson.annotation.JsonCreator";
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
        resolveJsonCreatorConflict(element, entity);
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

        GeneratedETagUtils.synthesizeColumnTransformer(entity, properties);

        if (entity.hasAnnotation(JSON_VIEW_ANNOTATION) || entity.hasAnnotation(JSON_SUB_VIEW_ANNOTATION)) {
            validateJsonView(entity, context);
        }
    }

    /**
     * Jackson's {@code @JsonCreator} is mapped to {@link io.micronaut.core.annotation.Creator}, which makes it the
     * single creator exposed by the bean introspection. When its arguments are not persistent properties (a value
     * object built from one JSON value, see issue #3752) the introspection creator cannot be used to instantiate
     * the type from the persisted columns. The type is then instantiated with its no-argument constructor and
     * populated through setters, which is recorded with {@link InstantiateWithDefaultConstructor}; when that is not
     * possible compilation fails with a description of the conflict instead of a mapping error at runtime.
     *
     * @param element The entity or embeddable
     * @param entity  The source persistent entity
     */
    private void resolveJsonCreatorConflict(ClassElement element, SourcePersistentEntity entity) {
        MethodElement creator = element.getPrimaryConstructor().orElse(null);
        if (creator == null || !isJsonCreator(creator)) {
            return;
        }
        List<String> unmappedArguments = new ArrayList<>();
        for (ParameterElement parameter : creator.getParameters()) {
            if (entity.getPropertyByName(parameter.getName()) == null) {
                unmappedArguments.add(parameter.getName());
            }
        }
        if (unmappedArguments.isEmpty()) {
            return;
        }
        // getPersistentPropertyNames() and getPropertyByName() are backed by the same map, which also holds the
        // identity, the composite identity components and the version, so both checks above and here see the very
        // same definition of "persistent property"
        List<String> readOnlyProperties = new ArrayList<>();
        for (String propertyName : entity.getPersistentPropertyNames()) {
            SourcePersistentProperty property = entity.getPropertyByName(propertyName);
            if (property != null && property.getPropertyElement().isReadOnly()) {
                readOnlyProperties.add(propertyName);
            }
        }
        boolean hasDefaultConstructor = element.getDefaultConstructor().isPresent();
        if (hasDefaultConstructor && readOnlyProperties.isEmpty()) {
            element.annotate(InstantiateWithDefaultConstructor.class);
            return;
        }
        List<String> reasons = new ArrayList<>(2);
        if (!hasDefaultConstructor) {
            reasons.add("there is no accessible no-argument constructor");
        }
        if (!readOnlyProperties.isEmpty()) {
            reasons.add("the properties " + readOnlyProperties + " cannot be set after construction");
        }
        throw new ProcessingException(creator, "@JsonCreator " + describeCreator(element, creator)
            + " is the bean introspection creator of [" + element.getName() + "] but its argument(s) " + unmappedArguments
            + " are not persistent properties, so Micronaut Data cannot instantiate the type from the persisted columns, and "
            + String.join(" and ", reasons)
            + ". Either add a no-argument constructor and setters for all persistent properties, "
            + "or remove @JsonCreator and use a custom Serde deserializer (@Serdeable.Deserializable(using = ...)).");
    }

    private static boolean isJsonCreator(MethodElement element) {
        return element.hasAnnotation(JSON_CREATOR_ANNOTATION) || element.hasAnnotation(JACKSON3_JSON_CREATOR_ANNOTATION);
    }

    private static String describeCreator(ClassElement element, MethodElement creator) {
        String name = creator instanceof ConstructorElement ? element.getSimpleName() : element.getSimpleName() + "." + creator.getName();
        return name + "(" + Arrays.stream(creator.getParameters())
            .map(parameter -> parameter.getType().getSimpleName() + " " + parameter.getName())
            .collect(Collectors.joining(", ")) + ")";
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

}
