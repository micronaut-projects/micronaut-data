package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link TypeElementVisitor} that pre-computes mappings to columns based on the configured naming strategy.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MappedEntityVisitor implements TypeElementVisitor<MappedEntity, Object> {

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        NamingStrategy namingStrategy = resolveNamingStrategy(element);
        Optional<String> targetName = element.stringValue(MappedEntity.class);
        SourcePersistentEntity entity = new SourcePersistentEntity(element);
        if (!targetName.isPresent()) {
            element.annotate(MappedEntity.class, builder -> {

                String mappedName = namingStrategy.mappedName(entity);
                builder.value(mappedName);
            });
        }
        List<PersistentProperty> properties = entity.getPersistentProperties();
        for (PersistentProperty property : properties) {
            computeMappingDefaults(element, context, namingStrategy, property);
        }
        SourcePersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            computeMappingDefaults(element, context, namingStrategy, identity);
        }
    }

    private void computeMappingDefaults(ClassElement element, VisitorContext context, NamingStrategy namingStrategy, PersistentProperty property) {
        AnnotationMetadata annotationMetadata = property.getAnnotationMetadata();
        SourcePersistentProperty spp = (SourcePersistentProperty) property;
        PropertyElement propertyElement = spp.getPropertyElement();
        Optional<String> mapping = annotationMetadata.stringValue(MappedProperty.class);
        if (!mapping.isPresent()) {
            propertyElement.annotate(MappedProperty.class, builder -> builder.value(namingStrategy.mappedName(spp)));
        }
        DataType dataType = annotationMetadata.findAnnotation(MappedProperty.class)
                .flatMap(av -> av.enumValue("type", DataType.class))
                .orElse(null);

        if (dataType == null) {
            ClassElement type = propertyElement.getType();
            String typeName = type.getName();
            if (type.isPrimitive() || typeName.startsWith("java.lang")) {
                Class primitiveType = ClassUtils.getPrimitiveType(typeName).orElse(null);
                if (primitiveType != null) {

                    ReflectionUtils.getWrapperType(primitiveType);
                    DataType dt = DataType.valueOf(typeName.toUpperCase(Locale.ENGLISH));
                    if (type.isArray()) {
                        if (dt == DataType.BYTE) {
                            propertyElement.annotate(MappedProperty.class, builder ->
                                    builder.member("type", DataType.BYTE_ARRAY)
                            );
                        }
                    } else {
                        propertyElement.annotate(MappedProperty.class, builder -> builder.member("type", dt));
                    }
                } else {
                    String simpleName = type.getSimpleName();
                    try {
                        DataType dt = DataType.valueOf(simpleName.toUpperCase(Locale.ENGLISH));
                        propertyElement.annotate(MappedProperty.class, builder -> builder.member("type", dt));
                    } catch (IllegalArgumentException e) {
                        context.fail("Unknown primitive or basic type: " + typeName, element);
                    }
                }
            } else if (ClassUtils.isJavaBasicType(typeName)) {
                if (type.isAssignable(CharSequence.class)) {
                    propertyElement.annotate(MappedProperty.class, builder -> builder.member("type", DataType.STRING));
                } else if (type.isAssignable(BigDecimal.class)) {
                    propertyElement.annotate(MappedProperty.class, builder -> builder.member("type", DataType.BIGDECIMAL));
                } else if (type.isAssignable(Temporal.class) || type.isAssignable(Date.class)) {
                    propertyElement.annotate(MappedProperty.class, builder -> builder.member("type", DataType.DATE));
                } else {
                    propertyElement.annotate(MappedProperty.class, builder -> builder.member("type", DataType.STRING));
                }
            }
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
}
