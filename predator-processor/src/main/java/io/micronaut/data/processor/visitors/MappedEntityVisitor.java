package io.micronaut.data.processor.visitors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
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

import java.util.*;
import java.util.function.Function;

/**
 * A {@link TypeElementVisitor} that pre-computes mappings to columns based on the configured naming strategy.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MappedEntityVisitor implements TypeElementVisitor<MappedEntity, Object> {
    private Map<String, SourcePersistentEntity> entityMap = new HashMap<>(50);
    private final Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<ClassElement, SourcePersistentEntity>() {
        @Override
        public SourcePersistentEntity apply(ClassElement classElement) {
            return entityMap.computeIfAbsent(classElement.getName(), s -> new SourcePersistentEntity(classElement, this));
        }
    };

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        NamingStrategy namingStrategy = resolveNamingStrategy(element);
        Optional<String> targetName = element.stringValue(MappedEntity.class);
        SourcePersistentEntity entity = entityResolver.apply(element);
        if (!targetName.isPresent()) {
            element.annotate(MappedEntity.class, builder -> {

                String mappedName = namingStrategy.mappedName(entity);
                builder.value(mappedName);
            });
        }
        Map<String, DataType> dataTypes = getConfiguredDataTypes(element);
        List<SourcePersistentProperty> properties = entity.getPersistentProperties();
        for (PersistentProperty property : properties) {
            computeMappingDefaults(namingStrategy, property, dataTypes);
        }
        SourcePersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            computeMappingDefaults(namingStrategy, identity, dataTypes);
        }
    }

    /**
     * Resolves the configured data types.
     * @param element The element
     * @return The data types
     */
    static Map<String, DataType> getConfiguredDataTypes(ClassElement element) {
        List<AnnotationValue<TypeDef>> typeDefinitions = element.getAnnotationValuesByType(TypeDef.class);
        Map<String, DataType> dataTypes = new HashMap<>(typeDefinitions.size());
        for (AnnotationValue<TypeDef> typeDefinition : typeDefinitions) {
            typeDefinition.enumValue("type", DataType.class).ifPresent(dataType -> {
                String[] values = typeDefinition.stringValues("classes");
                String[] names = typeDefinition.stringValues("names");
                String[] concated = ArrayUtils.concat(values, names);
                for (String s : concated) {
                    dataTypes.put(s, dataType);
                }
            });
        }
        return dataTypes;
    }

    private void computeMappingDefaults(NamingStrategy namingStrategy, PersistentProperty property, Map<String, DataType> dataTypes) {
        AnnotationMetadata annotationMetadata = property.getAnnotationMetadata();
        SourcePersistentProperty spp = (SourcePersistentProperty) property;
        PropertyElement propertyElement = spp.getPropertyElement();

        DataType dataType = annotationMetadata.getValue(TypeDef.class, "type", DataType.class)
                .orElse(null);

        if (dataType == null) {
            ClassElement type = propertyElement.getType();
            dataType = TypeUtils.resolveDataType(type, dataTypes);
        }

        if (dataType == DataType.ENTITY && !propertyElement.hasStereotype(Relation.class)) {
            propertyElement = (PropertyElement) propertyElement.annotate(Relation.class, builder ->
                builder.value(Relation.Kind.MANY_TO_ONE)
            );
        }

        Optional<String> mapping = annotationMetadata.stringValue(MappedProperty.class);
        if (!mapping.isPresent()) {
            propertyElement.annotate(MappedProperty.class, builder -> builder.value(namingStrategy.mappedName(spp)));
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
