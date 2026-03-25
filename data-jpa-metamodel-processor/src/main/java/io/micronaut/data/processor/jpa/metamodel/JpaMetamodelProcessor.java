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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.*;
import io.micronaut.sourcegen.model.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.lang.annotation.Annotation;
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
     * Jakarta persistence AccessType enum.
     */
    public enum JakartaAccessType {
        /**
         * Jakarta persistence AccessType Field.
         */
        FIELD,
        /**
         * Jakarta persistence AccessType Property.
         */
        PROPERTY
    }

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
     * Supported Jakarta annotations for generating Static meta model classes.
     */
    public static final Set<String> SUPPORTED_JAKARTA_ANNOTATIONS = new HashSet<>(Arrays.asList(JAKARTA_ENTITY,
        JAKARTA_MAPPED_SUPER_CLASS,
        JAKARTA_EMBEDDABLE));

    /**
     * Default constructor.
     */
    public JpaMetamodelProcessor() {
    }

    /**
     * JPA meta model class def generator .
     *
     * @param packageName          Element package name
     * @param element              Class Element
     * @return Jpa metamodel class definition builder .
     */
    public static ClassDef.ClassDefBuilder createJpaMetaModelClassDefBuilder(@NonNull String packageName, @NonNull ClassElement element) {
        ClassTypeDef elementType = ClassTypeDef.of(element);
        String metaModelClassName = resolveModelClassName(packageName, elementType);

        List<? extends Element> fieldElements = resolveFieldElements(element, elementType.getName(), element.getAnnotation(JAKARTA_ACCESS));

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(metaModelClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationDef.builder(ClassTypeDef.of(JAKARTA_STATIC_METAMODEL)).addMember("value", elementType).build())
            .addAnnotation(AnnotationDef.builder(ClassTypeDef.of(JAKARTA_ANNOTATION_GENERATED)).addMember("value", JpaMetamodelProcessor.class.getName()).build());

        ClassElement superElement = element.getSuperType().orElse(null);

        if (superElement != null && supportedClass(superElement)) {
            String superElementModelClassName = resolveModelClassName(superElement.getPackageName(), ClassTypeDef.of(superElement));
            ClassTypeDef superClassModelTypeDef = ClassTypeDef.of(superElementModelClassName);
            classDefBuilder.superclass(superClassModelTypeDef);
        }

        List<FieldDef> constantPropertyName = new ArrayList<>();
        List<FieldDef> attributeFields = new ArrayList<>();

        for (Element fieldElement : fieldElements) {
            if (fieldElement instanceof TypedElement typedElement) {
                constantPropertyName.add(createConstantPropertyName(typedElement.getName()));
                attributeFields.add(createAttributeField(fieldElement.getName(), typedElement.getType(), elementType));
            }
        }

        classDefBuilder.addFields(constantPropertyName);
        classDefBuilder.addFields(attributeFields);
        classDefBuilder.addField(createJakartaManagedEntityTypeField(elementType, element.getAnnotationNames()));
        return classDefBuilder;
    }

    /**
     * Resolves field elements based on the access type.
     * @param element Class element.
     * @param elementType Class element type.
     * @param jakartaAccessAnnotation Jakarta access annotation value.
     * @return List of supported fields for the static metamodel.
     */
    private static List<? extends Element> resolveFieldElements(@NonNull ClassElement element, String elementType, @Nullable AnnotationValue<Annotation> jakartaAccessAnnotation) {
        JakartaAccessType jakartaAccessType = resolveAccessType(element, jakartaAccessAnnotation);

        List<Element> elements = switch (jakartaAccessType) {
            case FIELD -> new ArrayList<>(element.getFields());
            case PROPERTY -> {
                List<Element> properties = new ArrayList<>(element.getBeanProperties());
                element.getFields().stream().filter(o -> o.getAnnotation(JAKARTA_ACCESS) != null && o.getAnnotation(JAKARTA_ACCESS).getRequiredValue(JakartaAccessType.class)
                        .equals(JakartaAccessType.FIELD))
                    .forEach(properties::add);
                yield properties;
            }
        };
        elements = elements.stream()
            .filter(o -> !o.getModifiers().contains(ElementModifier.STATIC))
            .filter(o -> !o.getModifiers().contains(ElementModifier.TRANSIENT) && !o.getAnnotationNames().contains(JAKARTA_TRANSIENT))
            .filter((o) -> {
                if (o instanceof MemberElement memberElement) {
                    return memberElement.getDeclaringType().getName().equals(elementType);
                }
                return false;
            }).toList();

        return elements;
    }

    /**
     * Resolves the access type.
     * @param element Class element.
     * @param jakartaAccessAnnotation Jakarta access annotation value.
     * @return Jakarta access type.
     */
    private static JakartaAccessType resolveAccessType(@NonNull ClassElement element, @Nullable AnnotationValue<Annotation> jakartaAccessAnnotation) {
        if (jakartaAccessAnnotation == null &&
            element.getMethods().stream().anyMatch(o -> o.hasAnnotation(JAKARTA_ID) ||
                element.hasAnnotation(JAKARTA_EMBEDDED_ID))) {
            return JakartaAccessType.PROPERTY;
        }
        if (jakartaAccessAnnotation == null) {
            return JakartaAccessType.FIELD;
        }
        return jakartaAccessAnnotation.getRequiredValue(JakartaAccessType.class);

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
     * @param classElement class element
     * @return boolean
     */
    public static boolean supportedClass(ClassElement classElement) {
        return !classElement.isInner() && classElement.getAnnotationNames().stream().anyMatch(SUPPORTED_JAKARTA_ANNOTATIONS::contains);
    }

    /**
     * Utility function to create jakarta managed type field.
     * @param elementType class type definition
     * @return FieldDef
     */
    private static FieldDef createJakartaManagedEntityTypeField(ClassTypeDef elementType, Set<String> classAnnotations) {
        String jakartaManegedType = resolveJakartaManegedType(classAnnotations);

        return FieldDef.builder("class_")
            .addModifiers(Modifier.PUBLIC, Modifier.VOLATILE, Modifier.STATIC)
            .ofType(TypeDef.parameterized(ClassTypeDef.of(jakartaManegedType), elementType)).build();
    }

    /**
     * Utility function to resolve the jakarta managed type based on the annotations on the classElement.
     * @param classAnnotations set of annotation names found on the class element.
     * @return jakarta managed type name.
     */
    private static String resolveJakartaManegedType(Set<String> classAnnotations) {
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
