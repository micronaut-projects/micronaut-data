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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Utilities for inspecting the generic signature of a change-listener method.
 */
final class ChangeListenerMethodUtils {

    private ChangeListenerMethodUtils() {
    }

    /**
     * Resolves the persistent entity type from a valid {@code ChangeEvent<E>} method argument.
     *
     * @param element The listener method.
     * @return The entity type, or {@code null} when the method does not declare a concrete event type.
     */
    static @Nullable ClassElement resolveEntityType(MethodElement element) {
        ParameterElement[] parameters = element.getParameters();
        if (parameters.length != 1) {
            return null;
        }
        return resolveEntityType(parameters[0].getGenericType());
    }

    /**
     * Resolves the entity type from a {@code ChangeEvent<E>} type.
     *
     * @param eventType The event argument type.
     * @return The entity type, or {@code null} when it is raw, wildcarded, or unresolved.
     */
    static @Nullable ClassElement resolveEntityType(ClassElement eventType) {
        if (!ChangeListenerVisitor.CHANGE_EVENT.equals(eventType.getName()) || eventType.isRawType()) {
            return null;
        }
        Map<String, ClassElement> typeArguments = eventType.getTypeArguments();
        if (typeArguments.size() != 1) {
            return null;
        }
        ClassElement entityType = typeArguments.values().iterator().next();
        return entityType.isWildcard() || entityType.isGenericPlaceholder() ? null : entityType;
    }
}
