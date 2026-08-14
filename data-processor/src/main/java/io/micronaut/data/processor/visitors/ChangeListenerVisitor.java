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

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Validates the database-neutral method contract for change listeners.
 */
public final class ChangeListenerVisitor implements TypeElementVisitor<Object, Object> {
    static final String CHANGE_LISTENER = "io.micronaut.data.jdbc.annotation.ChangeListener";
    static final String CHANGE_EVENT = "io.micronaut.data.jdbc.notification.ChangeEvent";

    @Override
    public int getOrder() {
        return MappedEntityVisitor.POSITION + 1;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!element.hasAnnotation(CHANGE_LISTENER)) {
            return;
        }
        if (element.isPrivate() || element.isStatic()) {
            context.fail("@ChangeListener method must be a non-private instance method", element);
            return;
        }
        if (!element.getReturnType().isVoid()) {
            context.fail("@ChangeListener method must return void", element);
            return;
        }

        ParameterElement[] parameters = element.getParameters();
        if (parameters.length != 1) {
            context.fail("@ChangeListener method must declare exactly one ChangeEvent argument", element);
            return;
        }

        ClassElement eventType = parameters[0].getGenericType();
        if (!CHANGE_EVENT.equals(eventType.getName())) {
            context.fail("@ChangeListener method argument must be ChangeEvent<E>", element);
            return;
        }
        ClassElement entityType = ChangeListenerMethodUtils.resolveEntityType(eventType);
        if (entityType == null) {
            context.fail("@ChangeListener ChangeEvent argument must declare one concrete entity type", element);
            return;
        }
        if (!entityType.hasStereotype(MappedEntity.class)) {
            context.fail("@ChangeListener ChangeEvent type argument must be a persistent entity", element);
        }
    }
}
