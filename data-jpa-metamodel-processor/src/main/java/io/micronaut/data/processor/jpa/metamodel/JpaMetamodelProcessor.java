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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.sourcegen.model.*;
import org.jspecify.annotations.NonNull;

import javax.lang.model.element.Modifier;
import java.util.*;

/**
 * Jpa Static Metamodel processor.
 */
public final class JpaMetamodelProcessor {

    /**
     * Supported Jakarta annotations for generating Static meta model classes.
     */
    public static final Set<String> SUPPORTED_JAKARTA_ANNOTATIONS = new HashSet<>(Arrays.asList("jakarta.persistence.Entity",
        "jakarta.persistence.MappedSuperclass",
        "jakarta.persistence.Embeddable"));

    /**
     * Default constructor.
     */
    public JpaMetamodelProcessor() {
    }

    /**
     * JPA meta model class def generator .
     *
     * @param packageName          Element package name
     * @param elementType          Element type
     * @param optionalSuperElement Element super type
     * @param properties           element properties/fields
     * @return Jpa metamodel class definition builder .
     */
    public static ClassDef.ClassDefBuilder createJpaMetaModelClassDefBuilder(@NonNull String packageName, @NonNull ClassTypeDef elementType, Optional<ClassElement> optionalSuperElement, List<PropertyElement> properties) {
        String metaModelClassName = resolveModelClassName(packageName, elementType);

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(metaModelClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationDef.builder(ClassTypeDef.of("jakarta.persistence.metamodel.StaticMetamodel")).addMember("value", elementType).build())
            .addAnnotation(AnnotationDef.builder(ClassTypeDef.of("jakarta.annotation.Generated")).addMember("value", JpaMetamodelProcessor.class.getName()).build());

        ClassElement superElement = optionalSuperElement.orElse(null);

        if (superElement != null && supportedClass(superElement)) {
            String superElementModelClassName = resolveModelClassName(superElement.getPackageName(), ClassTypeDef.of(superElement));
            ClassTypeDef superClassModelTypeDef = ClassTypeDef.of(superElementModelClassName);
            classDefBuilder.superclass(superClassModelTypeDef);
        }

        properties = properties.stream().filter(o -> !o.getAnnotationNames().contains("jakarta.persistence.Transient"))
            .filter(o -> o.getDeclaringType().getName().equals(elementType.getName()))
            .toList();

        List<FieldDef> constantPropertyName = new ArrayList<>();
        List<FieldDef> attributeFields = new ArrayList<>();

        for (PropertyElement beanProperty : properties) {
            constantPropertyName.add(createConstantPropertyName(beanProperty));
            attributeFields.add(createAttributeField(beanProperty, elementType));
        }

        classDefBuilder.addFields(constantPropertyName);
        classDefBuilder.addFields(attributeFields);
        classDefBuilder.addField(createEntityTypeField(elementType));
        return classDefBuilder;
    }

    /**
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
     * @param classElement class element
     * @return boolean
     */
    public static boolean supportedClass(ClassElement classElement) {
        return !classElement.isInner() && classElement.getAnnotationNames().stream().anyMatch(SUPPORTED_JAKARTA_ANNOTATIONS::contains);
    }

    /**
     * @param elementType class type definition
     * @return FieldDef
     */
    private static FieldDef createEntityTypeField(ClassTypeDef elementType) {
        return FieldDef.builder("class_").addModifiers(Modifier.PUBLIC, Modifier.VOLATILE, Modifier.STATIC)
            .ofType(TypeDef.parameterized(ClassTypeDef.of("jakarta.persistence.metamodel.EntityType"), elementType)).build();
    }

    /**
     * @param beanProperty field
     * @return FieldDef
     */
    private static FieldDef createConstantPropertyName(PropertyElement beanProperty) {
        return FieldDef.builder(NameUtils.underscoreSeparate(beanProperty.getName()).toUpperCase(Locale.ROOT))
            .ofType(TypeDef.STRING)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
            .initializer(ExpressionDef.constant(beanProperty.getSimpleName())).build();
    }

    /**
     * Create attribute fields SingularAttribute,ListAttribute... based on the element type .
     *
     * @param beanProperty Field
     * @param classTypeDef Field type
     * @return FieldDef
     */
    private static FieldDef createAttributeField(PropertyElement beanProperty, ClassTypeDef classTypeDef) {
        FieldDef.FieldDefBuilder attributeDefBuilder = FieldDef.builder(beanProperty.getName()).addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.VOLATILE);

        TypeDef typeDef = switch (beanProperty.getType().getName()) {
            case "java.util.Collection" ->
                TypeDef.parameterized(ClassTypeDef.of("jakarta.persistence.metamodel.CollectionAttribute"), classTypeDef, TypeDef.of(beanProperty.getType().getTypeArguments().get("E")));
            case "java.util.Set" ->
                TypeDef.parameterized(ClassTypeDef.of("jakarta.persistence.metamodel.SetAttribute"), classTypeDef, TypeDef.of(beanProperty.getType().getTypeArguments().get("E")));
            case "java.util.List" ->
                TypeDef.parameterized(ClassTypeDef.of("jakarta.persistence.metamodel.ListAttribute"), classTypeDef, TypeDef.of(beanProperty.getType().getTypeArguments().get("E")));
            case "java.util.Map" ->
                TypeDef.parameterized(ClassTypeDef.of("jakarta.persistence.metamodel.MapAttribute"), classTypeDef, TypeDef.of(beanProperty.getType().getTypeArguments().get("K")), TypeDef.of(beanProperty.getType().getTypeArguments().get("V")));
            default ->
                TypeDef.parameterized(ClassTypeDef.of("jakarta.persistence.metamodel.SingularAttribute"), classTypeDef, getProperType(TypeDef.of(beanProperty.getType())));
        };
        return attributeDefBuilder.ofType(typeDef).build();
    }

    /**
     *
     * @param type
     * @return
     */
    private static TypeDef getProperType(TypeDef type) {
        if (type.isPrimitive() && type instanceof TypeDef.Primitive primitive) {
            return TypeDef.of(primitive.wrapperType().getName());
        }
        return type;
    }
}
