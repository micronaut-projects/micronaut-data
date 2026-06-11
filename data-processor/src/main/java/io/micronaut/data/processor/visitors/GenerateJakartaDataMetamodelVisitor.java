/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GenerateJakartaDataMetamodel;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.PackageElementVisitor;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.micronaut.data.processor.visitors.MetamodelTypes.JAKARTA_ANNOTATION_GENERATED;
import static io.micronaut.data.processor.visitors.MetamodelTypes.JAKARTA_DATA_BASIC_ATTRIBUTE;
import static io.micronaut.data.processor.visitors.MetamodelTypes.JAKARTA_DATA_BOOLEAN_ATTRIBUTE;
import static io.micronaut.data.processor.visitors.MetamodelTypes.JAKARTA_DATA_COMPARABLE_ATTRIBUTE;
import static io.micronaut.data.processor.visitors.MetamodelTypes.JAKARTA_DATA_NAVIGABLE_ATTRIBUTE;
import static io.micronaut.data.processor.visitors.MetamodelTypes.JAKARTA_DATA_NUMERIC_ATTRIBUTE;
import static io.micronaut.data.processor.visitors.MetamodelTypes.JAKARTA_DATA_SORTABLE_ATTRIBUTE;
import static io.micronaut.data.processor.visitors.MetamodelTypes.JAKARTA_DATA_STATIC_METAMODEL;
import static io.micronaut.data.processor.visitors.MetamodelTypes.JAKARTA_DATA_TEMPORAL_ATTRIBUTE;
import static io.micronaut.data.processor.visitors.MetamodelTypes.JAKARTA_DATA_TEXT_ATTRIBUTE;
import static io.micronaut.data.processor.visitors.MetamodelTypes.isBoolean;
import static io.micronaut.data.processor.visitors.MetamodelTypes.isNumeric;
import static io.micronaut.data.processor.visitors.MetamodelTypes.isTemporal;
import static io.micronaut.data.processor.visitors.MetamodelTypes.isText;
import static io.micronaut.data.processor.visitors.MetamodelTypes.isUuid;

/**
 * The Jakarta Data static metamodel generator.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public class GenerateJakartaDataMetamodelVisitor implements TypeElementVisitor<GenerateJakartaDataMetamodel, Object>, PackageElementVisitor<GenerateJakartaDataMetamodel> {

    /**
     * Map of already processed entities.
     */
    private final Set<String> processed = new HashSet<>();

    /**
     * Source Persistent entity registry.
     */
    private final Map<String, SourcePersistentEntity> entityMap = new HashMap<>();

    /**
     * Persistent Entity resolver.
     */
    private final Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<>() {
        @Override
        public SourcePersistentEntity apply(ClassElement classElement) {
            return entityMap.computeIfAbsent(classElement.getName(), s -> new SourcePersistentEntity(classElement, this));
        }
    };

    @Override
    public void visitPackage(PackageElement element, VisitorContext context) throws ProcessingException {
        for (ClassElement classElement : context.getClassElements(element)) {
            visitClass(classElement, context);
        }
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return TypeElementVisitor.super.getSupportedAnnotationNames();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasAnnotation(MappedEntity.class) && !element.hasAnnotation(Embeddable.class) && !processed.contains(element.getName())) {
            return;
        }

        SourcePersistentEntity persistentEntity = entityResolver.apply(element);
        try {
            ClassDef.ClassDefBuilder builder = createJDMetaModelClassDefBuilder(element.getPackageName(), persistentEntity);
            ClassDef builderDef = builder.build();
            SourceGenerator sourceGenerator = SourceGenerators.findByLanguage(context.getLanguage()).orElse(null);
            if (sourceGenerator == null) {
                return;
            }
            processed.add(element.getName());
            sourceGenerator.write(builderDef, context, element);
        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new ProcessingException(element, "Failed to generate a @" + JAKARTA_DATA_STATIC_METAMODEL + ": " + message, e);
        }
    }

    @Override
    public int getOrder() {
        return -100; // Run before repository element visitor
    }

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void start(VisitorContext visitorContext) {
        this.processed.clear();
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    private static ClassDef.ClassDefBuilder createJDMetaModelClassDefBuilder(String packageName, SourcePersistentEntity persistentEntity) {
        ClassElement classElement = persistentEntity.getClassElement();
        String metaModelClassName = resolveModelClassName(packageName, classElement);

        ClassDef.ClassDefBuilder classDefBuilder = ClassDef.builder(metaModelClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationDef.builder(ClassTypeDef.of(JAKARTA_DATA_STATIC_METAMODEL)).addMember("value", ClassTypeDef.of(classElement)).build())
            .addAnnotation(AnnotationDef.builder(ClassTypeDef.of(JAKARTA_ANNOTATION_GENERATED)).addMember("value", GenerateJakartaDataMetamodelVisitor.class.getName()).build());

        PersistentEntity parentEntity = persistentEntity.getParentEntity();

        if (parentEntity instanceof SourcePersistentEntity parentSourcePersistentEntity) {
            ClassElement parentSourcePersistentEntityClassElement = parentSourcePersistentEntity.getClassElement();
            String superElementModelClassName = resolveModelClassName(parentSourcePersistentEntityClassElement.getPackageName(), parentSourcePersistentEntityClassElement);
            ClassTypeDef superClassModelTypeDef = ClassTypeDef.of(superElementModelClassName);

            classDefBuilder.superclass(superClassModelTypeDef);
        }

        List<FieldDef> constantPropertyNames = new ArrayList<>();
        List<FieldDef> attributeFields = new ArrayList<>();

        for (String persistentPropertyName : persistentEntity.getPersistentPropertyNames()) {
            SourcePersistentProperty persistentProperty = persistentEntity.getPropertyByName(persistentPropertyName);
            if (persistentProperty == null) {
                throw new ProcessingException(persistentEntity, "Persistent property " + persistentPropertyName + " not found.");
            }
            if (!persistentProperty.getPropertyElement().getDeclaringType().getName().equals(classElement.getName())) {
                continue;
            }
            constantPropertyNames.add(createConstantPropertyName(persistentPropertyName));
            attributeFields.add(createAttributeField(persistentProperty, classElement));
        }

        classDefBuilder.addFields(constantPropertyNames);
        classDefBuilder.addFields(attributeFields);
        return classDefBuilder;
    }

    private static FieldDef createAttributeField(SourcePersistentProperty persistentProperty, ClassElement classElement) {
        String attributeType = resolveAttributeType(persistentProperty);
        TypeDef propertyTypeDef = boxPrimitive(TypeDef.of(persistentProperty.getType().getCanonicalName()));
        ClassTypeDef attributeClassTypeDef = ClassTypeDef.of(attributeType);

        List<TypeDef> generics = new ArrayList<>();
        generics.add(TypeDef.of(classElement));

        List<ExpressionDef> initializerParams = new ArrayList<>();
        initializerParams.add(ExpressionDef.constant(TypeDef.of(classElement)));
        initializerParams.add(ExpressionDef.constant(persistentProperty.getName()));

        if (attributeType.equals(JAKARTA_DATA_BOOLEAN_ATTRIBUTE)) {
            initializerParams.add(ExpressionDef.constant(propertyTypeDef));
        } else if (!attributeType.equals(JAKARTA_DATA_TEXT_ATTRIBUTE) &&
            !attributeType.equals(JAKARTA_DATA_SORTABLE_ATTRIBUTE)) {
            generics.add(propertyTypeDef);
            initializerParams.add(ExpressionDef.constant(propertyTypeDef));
        }

        TypeDef attributeTypeDef = TypeDef.parameterized(attributeClassTypeDef, generics);

        return FieldDef.builder(persistentProperty.getName())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .ofType(attributeTypeDef)
            .initializer(attributeClassTypeDef.invokeStatic("of",
                attributeTypeDef,
                initializerParams))
            .build();

    }

    private static String resolveAttributeType(SourcePersistentProperty persistentProperty) {
        String typeName = persistentProperty.getTypeName();
        boolean isArray = persistentProperty.getType().isArray();
        if (persistentProperty instanceof Association) {
            return JAKARTA_DATA_NAVIGABLE_ATTRIBUTE;
        }
        if (isArray) {
            return JAKARTA_DATA_BASIC_ATTRIBUTE;
        }
        if (persistentProperty.isEnum() || isUuid(typeName)) {
            return JAKARTA_DATA_COMPARABLE_ATTRIBUTE;
        }
        if (isBoolean(typeName)) {
            return JAKARTA_DATA_BOOLEAN_ATTRIBUTE;
        }
        if (isText(typeName)) {
            return JAKARTA_DATA_TEXT_ATTRIBUTE;
        }
        if (isNumeric(typeName)) {
            return JAKARTA_DATA_NUMERIC_ATTRIBUTE;
        }
        if (isTemporal(typeName)) {
            return JAKARTA_DATA_TEMPORAL_ATTRIBUTE;
        }
        return JAKARTA_DATA_BASIC_ATTRIBUTE;
    }

    private static FieldDef createConstantPropertyName(String persistentPropertyName) {
        return FieldDef.builder(persistentPropertyName.toUpperCase(Locale.getDefault()))
            .ofType(TypeDef.STRING)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
            .initializer(ExpressionDef.constant(persistentPropertyName)).build();
    }

    private static String resolveModelClassName(String packageName, ClassElement elementType) {
        String localBinaryName = elementType.getName().startsWith(packageName + ".") ? elementType.getName().substring(packageName.isEmpty() ? 0 : packageName.length() + 1) : elementType.getName();
        String baseName = elementType.isInner() ? localBinaryName.replace("$", "") : elementType.getSimpleName();
        String metaModelClassSimpleName = "_" + baseName;
        return packageName + "." + metaModelClassSimpleName;
    }

    private static TypeDef boxPrimitive(TypeDef type) {
        if (type.isPrimitive() && type instanceof TypeDef.Primitive primitive && !type.isArray()) {
            return TypeDef.of(primitive.wrapperType().getName());
        }
        return type;
    }
}
