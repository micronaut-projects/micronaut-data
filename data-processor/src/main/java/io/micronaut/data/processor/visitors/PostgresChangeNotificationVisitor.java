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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.nio.charset.StandardCharsets;

/**
 * Validates the PostgreSQL notification annotation and channel contract independently of
 * database-neutral listener signature validation.
 */
@Internal
public final class PostgresChangeNotificationVisitor implements TypeElementVisitor<Object, Object> {
    private static final String ANNOTATION = "io.micronaut.data.jdbc.annotation.PostgresChangeNotification";

    @Override
    public int getOrder() {
        return MappedEntityVisitor.POSITION + 10;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!element.hasAnnotation(ANNOTATION)) {
            return;
        }
        if (!element.hasStereotype(ChangeListenerVisitor.CHANGE_LISTENER)) {
            context.fail("@PostgresChangeNotification requires @ChangeListener", element);
        }
        String channel = element.stringValue(ANNOTATION, "channel").orElse("");
        if (channel.isBlank() || channel.indexOf('\0') >= 0 || channel.getBytes(StandardCharsets.UTF_8).length > 63) {
            context.fail("@PostgresChangeNotification channel must be non-blank, contain no NUL characters, "
                + "and occupy at most 63 UTF-8 bytes", element);
        }
    }
}
