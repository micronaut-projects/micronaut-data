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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.event.EntityEventMapping;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import javax.inject.Scope;

/**
 * Validates entity event method signatures.
 *
 * @author graemerocher
 * @since 2.3.0
 */
public class EntityEventVisitor implements TypeElementVisitor<Object, EntityEventMapping> {
    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        final String eventMapping = element.getAnnotationNameByStereotype(EntityEventMapping.class).orElse(null);
        if (eventMapping != null) {
            // validate signature
            if (element.isPrivate() || element.isStatic()) {
                context.fail("Method annotated with @" + NameUtils.getSimpleName(eventMapping) + " must be a non-private instance method", element);
            }

            if (element.hasStereotype(MappedEntity.class)) {
                if (!element.getReturnType().getName().equals("void") || element.getParameters().length != 0) {
                    context.fail("Method annotated with @" + NameUtils.getSimpleName(eventMapping) + " must return void and declare no arguments", element);
                }
            } else if (element.hasStereotype(Scope.class)) {
                if (!element.getReturnType().getName().equals("void") || element.getParameters().length != 1) {
                    context.fail("Method annotated with @" + NameUtils.getSimpleName(eventMapping) + " must return void and declare exactly one argument that represents the entity type to listen for", element);
                }
            }
        }
    }
}
