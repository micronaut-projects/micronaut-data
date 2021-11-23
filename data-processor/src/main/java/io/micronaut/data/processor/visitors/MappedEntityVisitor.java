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

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final Map<String, SourcePersistentEntity> entityMap = new HashMap<>(50);
    private final Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<ClassElement, SourcePersistentEntity>() {
        @Override
        public SourcePersistentEntity apply(ClassElement classElement) {
            return entityMap.computeIfAbsent(classElement.getName(), s -> new SourcePersistentEntity(classElement, this));
        }
    };
    private final boolean mappedEntity;

    /**
     * Default constructor.
     */
    public MappedEntityVisitor() {
        mappedEntity = true;
    }

    /**
     * @param mappedEntity Whether this applies to Mapped entity
     */
    MappedEntityVisitor(boolean mappedEntity) {
        this.mappedEntity = mappedEntity;
    }

    @Override
    public int getOrder() {
        // higher priority than the default
        return POSITION;
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        NamingStrategy namingStrategy = resolveNamingStrategy(element);
        Optional<String> targetName = element.stringValue(MappedEntity.class);
        SourcePersistentEntity entity = entityResolver.apply(element);
        if (isMappedEntity() && !targetName.isPresent()) {
            element.annotate(MappedEntity.class, builder -> {

                String mappedName = namingStrategy.mappedName(entity);
                builder.value(mappedName);
            });
        }
        Map<String, DataType> dataTypes = getConfiguredDataTypes(element);
        Map<String, String> dataConverters = getConfiguredDataConverters(element);

        List<SourcePersistentProperty> properties = entity.getPersistentProperties();

        final List<AnnotationValue<Index>> indexes = properties.stream()
                .filter(x -> ((PersistentProperty) x).findAnnotation(Indexes.class).isPresent())
                .map(prop -> prop.findAnnotation(Index.class))
                .flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
                .collect(Collectors.toList());

        if (!indexes.isEmpty()) {
           element.annotate(Indexes.class, builder -> builder.values(indexes.toArray(new AnnotationValue[]{})));
        }

        for (PersistentProperty property : properties) {
            computeMappingDefaults(namingStrategy, property, dataTypes, dataConverters, context);
        }
        SourcePersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            computeMappingDefaults(namingStrategy, identity, dataTypes, dataConverters, context);
        }
        SourcePersistentProperty[] compositeIdentities = entity.getCompositeIdentity();
        if (compositeIdentities != null) {
            for (SourcePersistentProperty compositeIdentity : compositeIdentities) {
                computeMappingDefaults(namingStrategy, compositeIdentity, dataTypes, dataConverters, context);
            }
        }
        SourcePersistentProperty version = entity.getVersion();
        if (version != null) {
            computeMappingDefaults(namingStrategy, version, dataTypes, dataConverters, context);
        }
    }

    private boolean isMappedEntity() {
        return mappedEntity;
    }

    private void computeMappingDefaults(
            NamingStrategy namingStrategy,
            PersistentProperty property,
            Map<String, DataType> dataTypes,
            Map<String, String> dataConverters,
            VisitorContext context) {

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
                context.fail("Relation cannot have converter specified", propertyElement);
                return;
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
                    context.fail("Cannot recognize proper data type. Please use @TypeDef to specify one", propertyElement);
                    return;
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
                    context.fail("Relation " + kind + " doesn't support 'mappedBy'.", propertyElement);
                }
            }
            if (kind == Relation.Kind.EMBEDDED) {
                // handled embedded
                SourcePersistentEntity embeddedEntity = entityResolver.apply(propertyElement.getType());
                List<SourcePersistentProperty> persistentProperties = embeddedEntity.getPersistentProperties();

                List<AnnotationValue<Property>> embeddedProperties = new ArrayList<>(persistentProperties.size());

                for (SourcePersistentProperty embeddedProperty : persistentProperties) {
                    if (!(embeddedProperty instanceof Association)) {
                        String mappedName = embeddedProperty.stringValue(MappedProperty.class)
                                .orElseGet(() -> namingStrategy.mappedName(
                                        property.getName() + embeddedProperty.getCapitilizedName()));
                        AnnotationValue<Property> av = AnnotationValue.builder(Property.class)
                                .value(mappedName)
                                .member("name", embeddedProperty.getName()).build();
                        embeddedProperties.add(av);
                    }
//                    else {
//                        // TODO: handle nested embedded
//                    }
                }

                propertyElement.annotate(MappedProperty.class, builder ->
                    builder.member(MappedProperty.EMBEDDED_PROPERTIES, embeddedProperties.toArray(new AnnotationValue[0]))
                );
            }
        }

        Optional<String> mapping = annotationMetadata.stringValue(MappedProperty.class);
        if (mappedEntity && !mapping.isPresent()) {
            propertyElement.annotate(MappedProperty.class, builder -> builder.value(namingStrategy.mappedName(spp)));
        }

        if (dataType != DataType.OBJECT) {
            DataType finalDataType = dataType;
            propertyElement.annotate(MappedProperty.class, builder -> builder.member("type", finalDataType));
        }
        if (converter != null) {
            String finalConverter = converter;
            propertyElement.annotate(MappedProperty.class, builder -> builder.member("converter", new AnnotationClassValue<>(finalConverter)));
        }
    }

    private NamingStrategy resolveNamingStrategy(ClassElement element) {
        return element.stringValue(MappedEntity.class, "namingStrategy")
                .flatMap(new Function<String, Optional<NamingStrategy>>() {
                    @Override
                    public Optional<NamingStrategy> apply(String s) {
                        Object o = InstantiationUtils.tryInstantiate(s, getClass().getClassLoader()).orElse(null);
                        if (o instanceof NamingStrategy) {
                            return Optional.of((NamingStrategy) o);
                        }
                        return Optional.empty();
                    }
                }).orElseGet(NamingStrategies.UnderScoreSeparatedLowerCase::new);
    }

    private DataType getDataTypeFromConverter(ClassElement type, String converter, Map<String, DataType> dataTypes, VisitorContext context) {
        ClassElement classElement = context.getClassElement(converter).get();
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

    private ClassElement getPersistedClassFromConverter(String converter, VisitorContext context) {
        ClassElement classElement = context.getClassElement(converter).get();
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
}
