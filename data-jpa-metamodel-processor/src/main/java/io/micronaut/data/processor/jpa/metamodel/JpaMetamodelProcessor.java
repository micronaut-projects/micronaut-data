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
package io.micronaut.data.processor.jpa.metamodel;

import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.sourcegen.model.*;
import org.jspecify.annotations.NonNull;

import javax.lang.model.element.Modifier;
import java.util.*;

/**
 * Jpa Static Metamodel processor.
 */
public final class JpaMetamodelProcessor {

    /**
     * JPA MetaModel generation build time flag name.
     */
    public static final String JPA_METAMODEL_ENABLED_FLAG = "micronaut.data.jpa.metamodel.enabled";

    /**
     * Jakarta Generated annotation name.
     */
    public static final String JAKARTA_ANNOTATION_GENERATED = "jakarta.annotation.Generated";

    /**
     * Jakarta persistent Transient annotation name.
     */
    public static final String JAKARTA_TRANSIENT = "jakarta.persistence.Transient";

    /**
     * Jakarta persistence metamodel StaticMetamodel annotation name.
     */
    public static final String JAKARTA_STATIC_METAMODEL = "jakarta.persistence.metamodel.StaticMetamodel";

    /**
     * Jakarta persistence metamodel CollectionAttribute annotation name.
     */
    public static final String JAKARTA_METAMODEL_COLLECTION_ATTRIBUTE = "jakarta.persistence.metamodel.CollectionAttribute";

    /**
     * Jakarta persistence metamodel SetAttribute annotation name.
     */
    public static final String JAKARTA_METAMODEL_SET_ATTRIBUTE = "jakarta.persistence.metamodel.SetAttribute";

    /**
     * Jakarta persistence metamodel ListAttribute annotation name.
     */
    public static final String JAKARTA_METAMODEL_LIST_ATTRIBUTE = "jakarta.persistence.metamodel.ListAttribute";

    /**
     * Jakarta persistence metamodel MapAttribute annotation name.
     */
    public static final String JAKARTA_METAMODEL_MAP_ATTRIBUTE = "jakarta.persistence.metamodel.MapAttribute";

    /**
     * Jakarta persistence metamodel SingularAttribute annotation name.
     */
    public static final String JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE = "jakarta.persistence.metamodel.SingularAttribute";

    /**
     * Jakarta persistence metamodel EntityType annotation name.
     */
    public static final String JAKARTA_METAMODEL_ENTITY_TYPE = "jakarta.persistence.metamodel.EntityType";

    /**
     * Jakarta persistence metamodel EmbeddableType annotation name.
     */
    public static final String JAKARTA_METAMODEL_EMBEDDABLE_TYPE = "jakarta.persistence.metamodel.EmbeddableType";

    /**
     * Jakarta persistence metamodel MappedSuperclassType annotation name.
     */
    public static final String JAKARTA_METAMODEL_MAPPED_SUPER_CLASS_TYPE = "jakarta.persistence.metamodel.MappedSuperclassType";

    /**
     * Jakarta persistence Entity annotation name.
     */
    public static final String JAKARTA_ENTITY = "jakarta.persistence.Entity";

    /**
     * Jakarta persistence MappedSuperClass annotation name.
     */
    public static final String JAKARTA_MAPPED_SUPER_CLASS = "jakarta.persistence.MappedSuperclass";

    /**
     * Jakarta persistence Embeddable annotation name.
     */
    public static final String JAKARTA_EMBEDDABLE = "jakarta.persistence.Embeddable";

    /**
     * Jakarta persistence Access annotation name.
     */
    public static final String JAKARTA_ACCESS = "jakarta.persistence.Access";

    /**
     * Jakarta persistence Access annotation name.
     */
    public static final String JAKARTA_ID = "jakarta.persistence.Id";

    /**
     * Jakarta persistence EmbeddedId annotation name.
     */
    public static final String JAKARTA_EMBEDDED_ID = "jakarta.persistence.EmbeddedId";

    /**
     * Micronaut data MappedEntity annotation name.
     */
    public static final String MICRONAUT_DATA_MAPPED_ENTITY = "io.micronaut.data.annotation.MappedEntity";

    /**
     * Java util Collection class name.
     */
    public static final String JAVA_UTIL_COLLECTION = Collection.class.getName();

    /**
     * Java util List class name.
     */
    public static final String JAVA_UTIL_LIST = List.class.getName();

    /**
     * Java util Set class name.
     */
    public static final String JAVA_UTIL_SET = Set.class.getName();

    /**
     * Java util Map class name.
     */
    public static final String JAVA_UTIL_MAP = Map.class.getName();

    /**
     * Supported annotations for generating Jakarta Static metamodel classes.
     */
    public static final Set<String> SUPPORTED_ANNOTATIONS = new HashSet<>(Arrays.asList(JAKARTA_ENTITY,
        JAKARTA_MAPPED_SUPER_CLASS,
        JAKARTA_EMBEDDABLE,
        MICRONAUT_DATA_MAPPED_ENTITY));

    /**
     * Default constructor.
     */
    public JpaMetamodelProcessor() {
    }

    /**
     * JPA meta model class def generator.
     * @param packageName Element package name.
     * @param classTypeDef Element Type.
     * @param persistentEntity Element persistent entity.
     * @return Static metamodel class definition builder.
     */
    public static ClassDef.ClassDefBuilder createJpaMetaModelClassDefBuilder(@NonNull String packageName, @NonNull ClassTypeDef classTypeDef, @NonNull SourcePersistentEntity persistentEntity) {
        String metaModelClassName = resolveModelClassName(packageName, classTypeDef);

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(metaModelClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationDef.builder(ClassTypeDef.of(JAKARTA_STATIC_METAMODEL)).addMember("value", classTypeDef).build())
            .addAnnotation(AnnotationDef.builder(ClassTypeDef.of(JAKARTA_ANNOTATION_GENERATED)).addMember("value", JpaMetamodelProcessor.class.getName()).build());

        PersistentEntity parentEntity = persistentEntity.getParentEntity();

        if (parentEntity instanceof SourcePersistentEntity parentSourcePersistentEntity && supportedClass(parentSourcePersistentEntity)) {
            ClassElement parentSourcePersistentEntityClassElement = parentSourcePersistentEntity.getClassElement();
            String superElementModelClassName = resolveModelClassName(parentSourcePersistentEntityClassElement.getPackageName(), ClassTypeDef.of(parentSourcePersistentEntityClassElement));
            ClassTypeDef superClassModelTypeDef = ClassTypeDef.of(superElementModelClassName);

            classDefBuilder.superclass(superClassModelTypeDef);
        }

        List<FieldDef> constantPropertyName = new ArrayList<>();
        List<FieldDef> attributeFields = new ArrayList<>();

        for (String persistentPropertyName : persistentEntity.getPersistentPropertyNames()) {
            SourcePersistentProperty persistentProperty = persistentEntity.getPropertyByName(persistentPropertyName);
            if (persistentProperty == null) {
                throw new ProcessingException(persistentEntity, "Persistent property " + persistentPropertyName + " not found.");
            }
            if (!persistentProperty.getDeclaringType().getName().equals(classTypeDef.getName())) {
                continue;
            }
            constantPropertyName.add(createConstantPropertyName(persistentPropertyName));
            attributeFields.add(createAttributeField(persistentPropertyName, persistentProperty.getType(), classTypeDef));
        }

        classDefBuilder.addFields(constantPropertyName);
        classDefBuilder.addFields(attributeFields);
        classDefBuilder.addField(createJakartaManagedEntityTypeField(classTypeDef, persistentEntity.getAnnotationNames()));
        return classDefBuilder;
    }

    /**
     * Utility function to resolve the canonical name for the StaticMetamodel class_.
     *
     * @param packageName package name
     * @param elementType element type
     * @return static metamodel class canonical name_ .
     */
    private static String resolveModelClassName(String packageName, ClassTypeDef elementType) {
        String localBinaryName = elementType.getName().startsWith(packageName + ".") ? elementType.getName().substring(packageName.isEmpty() ? 0 : packageName.length() + 1) : elementType.getName();
        String baseName = elementType.isInner() ? localBinaryName.replace("$", "") : elementType.getSimpleName();
        String metaModelClassSimpleName = baseName + "_";
        return packageName + "." + metaModelClassSimpleName;
    }

    /**
     * Utility function to check if the given class is supported for StaticMetamodel generation.
     *
     * @param sourcePersistentEntity source persistent element.
     * @return boolean.
     */
    public static boolean supportedClass(SourcePersistentEntity sourcePersistentEntity) {
        return supportedClass(sourcePersistentEntity.getClassElement());
    }

    /**
     * Utility function to check if the given class is supported for StaticMetamodel generation.
     *
     * @param classElement class element.
     * @return boolean.
     */
    public static boolean supportedClass(ClassElement classElement) {
        return !classElement.isInner() && classElement.getAnnotationNames().stream().anyMatch(SUPPORTED_ANNOTATIONS::contains);
    }

    /**
     * Utility function to create jakarta managed type field.
     * @param elementType class type definition
     * @return FieldDef
     */
    private static FieldDef createJakartaManagedEntityTypeField(ClassTypeDef elementType, Set<String> classAnnotations) {
        String jakartaManagedType = resolveJakartaManagedType(classAnnotations);

        return FieldDef.builder("class_")
            .addModifiers(Modifier.PUBLIC, Modifier.VOLATILE, Modifier.STATIC)
            .ofType(TypeDef.parameterized(ClassTypeDef.of(jakartaManagedType), elementType)).build();
    }

    /**
     * Utility function to resolve the jakarta managed type based on the annotations on the classElement.
     * @param classAnnotations set of annotation names found on the class element.
     * @return jakarta managed type name.
     */
    private static String resolveJakartaManagedType(Set<String> classAnnotations) {
        if (classAnnotations.contains(JAKARTA_MAPPED_SUPER_CLASS)) {
            return JAKARTA_METAMODEL_MAPPED_SUPER_CLASS_TYPE;
        } else if (classAnnotations.contains(JAKARTA_EMBEDDABLE)) {
            return JAKARTA_METAMODEL_EMBEDDABLE_TYPE;
        } else {
            return JAKARTA_METAMODEL_ENTITY_TYPE;
        }
    }

    /**
     * To create a constant field in the metamodel class_ with the field name as value .
     * @param fieldName field Name.
     * @return Field Definition.
     */
    private static FieldDef createConstantPropertyName(String fieldName) {
        return FieldDef.builder(NameUtils.underscoreSeparate(fieldName).toUpperCase(Locale.ROOT))
            .ofType(TypeDef.STRING)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
            .initializer(ExpressionDef.constant(fieldName)).build();
    }

    /**
     * Create attribute fields SingularAttribute,ListAttribute... based on the element type .
     * @param fieldName Field name
     * @param fieldType Field type
     * @param classTypeDef Class type def
     * @return Attribute field definition.
     */
    private static FieldDef createAttributeField(String fieldName, ClassElement fieldType, ClassTypeDef classTypeDef) {
        FieldDef.FieldDefBuilder attributeDefBuilder = FieldDef.builder(fieldName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.VOLATILE);

        Map<String, ClassElement> typeArguments = fieldType.getTypeArguments();

        TypeDef attributeTypeDef;
        String fieldTypeName = fieldType.getName();
        if (fieldTypeName.equals(JAVA_UTIL_COLLECTION) &&
            typeArguments.get("E") != null && !typeArguments.get("E").getName().equals(Object.class.getName())) {
            TypeDef e = TypeDef.of(typeArguments.get("E"));
            attributeTypeDef = TypeDef.parameterized(ClassTypeDef.of(JAKARTA_METAMODEL_COLLECTION_ATTRIBUTE), classTypeDef, e);

        } else if (fieldTypeName.equals(JAVA_UTIL_SET) &&
            typeArguments.get("E") != null && !typeArguments.get("E").getName().equals(Object.class.getName())) {
            TypeDef e = TypeDef.of(typeArguments.get("E"));
            attributeTypeDef = TypeDef.parameterized(ClassTypeDef.of(JAKARTA_METAMODEL_SET_ATTRIBUTE), classTypeDef, e);

        } else if (fieldTypeName.equals(JAVA_UTIL_LIST) &&
            typeArguments.get("E") != null && !typeArguments.get("E").getName().equals(Object.class.getName())) {
            TypeDef e = TypeDef.of(typeArguments.get("E"));
            attributeTypeDef = TypeDef.parameterized(ClassTypeDef.of(JAKARTA_METAMODEL_LIST_ATTRIBUTE), classTypeDef, e);

        } else if (fieldTypeName.equals(JAVA_UTIL_MAP) &&
            typeArguments.get("K") != null && !typeArguments.get("K").getName().equals(Object.class.getName()) &&
            typeArguments.get("V") != null && !typeArguments.get("V").getName().equals(Object.class.getName())) {

            TypeDef k = TypeDef.of(typeArguments.get("K"));
            TypeDef v = TypeDef.of(typeArguments.get("V"));
            attributeTypeDef = TypeDef.parameterized(ClassTypeDef.of(JAKARTA_METAMODEL_MAP_ATTRIBUTE), classTypeDef, k, v);

        } else {
            attributeTypeDef = TypeDef.parameterized(ClassTypeDef.of(JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE), classTypeDef, getProperType(TypeDef.of(fieldType)));
        }
        return attributeDefBuilder.ofType(attributeTypeDef).build();
    }

    /**
     * Utility function Returns the wrapper type if the provided type is a primitives.
     * @param type provided type.
     * @return wrapper type if the provided type is a primitives.
     */
    private static TypeDef getProperType(TypeDef type) {
        if (type.isPrimitive() && type instanceof TypeDef.Primitive primitive) {
            return TypeDef.of(primitive.wrapperType().getName());
        }
        return type;
    }
}
