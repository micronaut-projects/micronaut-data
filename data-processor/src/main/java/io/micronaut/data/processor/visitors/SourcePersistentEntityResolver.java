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
package io.micronaut.data.processor.visitors;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves source entities after applying their mapped-entity defaults.
 */
final class SourcePersistentEntityResolver implements Function<ClassElement, SourcePersistentEntity> {
    private static final String EMBEDDABLE = "io.micronaut.data.annotation.Embeddable";

    private final VisitorContext context;
    private final Map<String, SourcePersistentEntity> entityMap;
    private final MappedEntityVisitor mappedEntityVisitor = new MappedEntityVisitor();
    private final MappedEntityVisitor embeddedMappedEntityVisitor = new MappedEntityVisitor();

    SourcePersistentEntityResolver(VisitorContext context, Map<String, SourcePersistentEntity> entityMap) {
        this.context = context;
        this.entityMap = entityMap;
    }

    @Override
    public SourcePersistentEntity apply(ClassElement classElement) {
        String classNameKey = getClassNameKey(classElement);
        return entityMap.computeIfAbsent(classNameKey, ignored -> {
            if (classElement.hasAnnotation(EMBEDDABLE)) {
                embeddedMappedEntityVisitor.visitClass(classElement, context);
            } else {
                mappedEntityVisitor.visitClass(classElement, context);
            }
            return new SourcePersistentEntity(classElement, this);
        });
    }

    /**
     * Generates key for the entityMap using {@link ClassElement}.
     * If class element has generic types then will use all bound generic types in the key like
     * for example {@code Entity<CustomKeyType, CustomValueType>} and for non-generic class element
     * will just return class name.
     * This is needed when there are for example multiple embedded fields with the same type
     * but different generic type argument.
     *
     * @param classElement The class element
     * @return The key for entityMap created from the class element
     */
    private String getClassNameKey(ClassElement classElement) {
        List<? extends ClassElement> boundGenericTypes = classElement.getBoundGenericTypes();
        if (CollectionUtils.isNotEmpty(boundGenericTypes)) {
            StringBuilder keyBuff = new StringBuilder(classElement.getName());
            keyBuff.append("<");
            for (ClassElement boundGenericType : boundGenericTypes) {
                keyBuff.append(boundGenericType.getName());
                keyBuff.append(",");
            }
            keyBuff.deleteCharAt(keyBuff.length() - 1);
            keyBuff.append(">");
            return keyBuff.toString();
        } else {
            return classElement.getName();
        }
    }
}
