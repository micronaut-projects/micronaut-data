/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.inject.ast.ClassElement;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The mapping util class.
 */
@Internal
public final class Utils {

    public static SourcePersistentEntity resolvePersistentEntity(ClassElement returnType,
                                                                 Function<ClassElement, SourcePersistentEntity> entityResolver) {
        if (returnType != null) {
            if (returnType.hasAnnotation(MappedEntity.class) || returnType.hasStereotype(Embeddable.class)) {
                return entityResolver.apply(returnType);
            } else {
                Collection<ClassElement> typeArguments = returnType.getTypeArguments().values();
                for (ClassElement typeArgument : typeArguments) {
                    SourcePersistentEntity entity = resolvePersistentEntity(typeArgument, entityResolver);
                    if (entity != null) {
                        return entity;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Resolves the configured data types.
     * @param element The element
     * @return The data types
     */
    public static Map<String, DataType> getConfiguredDataTypes(ClassElement element) {
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

    /**
     * Resolves the configured data converters.
     * @param element The element
     * @return The data converters
     */
    public static Map<String, String> getConfiguredDataConverters(ClassElement element) {
        List<AnnotationValue<TypeDef>> typeDefinitions = element.getAnnotationValuesByType(TypeDef.class);
        Map<String, String> dataConverters = new HashMap<>(typeDefinitions.size());
        for (AnnotationValue<TypeDef> typeDefinition : typeDefinitions) {
            typeDefinition.stringValue("converter")
                    .filter(c -> !Object.class.getName().equals(c))
                    .ifPresent(converter -> {
                        String[] values = typeDefinition.stringValues("classes");
                        String[] names = typeDefinition.stringValues("names");
                        String[] concated = ArrayUtils.concat(values, names);
                        for (String s : concated) {
                            dataConverters.put(s, converter);
                        }
                    });
        }
        return dataConverters;
    }

}
