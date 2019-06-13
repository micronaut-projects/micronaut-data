package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;
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
            computeMappingDefaults(namingStrategy, property);
        }
        SourcePersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            computeMappingDefaults(namingStrategy, identity);
        }
    }

    private void computeMappingDefaults(NamingStrategy namingStrategy, PersistentProperty property) {
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
            dataType = TypeUtils.resolveDataType(type);
        }

        DataType finalDataType = dataType;
        propertyElement.annotate(MappedProperty.class, builder -> builder.member("type", finalDataType));
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
