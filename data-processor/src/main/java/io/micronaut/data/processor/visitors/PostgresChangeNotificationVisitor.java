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
import io.micronaut.data.annotation.ChangeListener;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.query.builder.sql.postgres.PostgresNotificationQuery;
import io.micronaut.data.processor.model.criteria.impl.SourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;

/**
 * Processes {@code @PostgresChangeNotification} methods during compilation.
 *
 * <p>The visitor verifies that the method is also a {@code @ChangeListener}, validates the
 * notification channel and supported primary-key mappings, and generates an internal
 * {@code PostgresChangeListenerQuery} annotation containing the entity reload query and the
 * mapped key columns and property paths in query binding order.</p>
 *
 * <p>This visitor performs compile-time processing only. It does not open a datasource
 * connection or install PostgreSQL notification functions or triggers.</p>
 */
@Internal
public final class PostgresChangeNotificationVisitor implements TypeElementVisitor<Object, Object> {
    private static final String ANNOTATION = "io.micronaut.data.jdbc.annotation.PostgresChangeNotification";
    private static final String POSTGRES_CHANGE_LISTENER_QUERY = "io.micronaut.data.jdbc.notification.postgres.PostgresChangeListenerQuery";

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(ANNOTATION);
    }

    @Override
    public String getElementType() {
        return ANNOTATION;
    }

    @Override
    public int getOrder() {
        return MappedEntityVisitor.POSITION + 10;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    /**
     * Validates PostgreSQL notification settings and generates the entity reload query at compilation time.
     *
     * @param element the annotated listener method
     * @param context the compilation visitor context used to report errors and add generated metadata
     */
    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!element.hasStereotype(ANNOTATION)) {
            return;
        }
        if (!element.hasStereotype(ChangeListener.class)) {
            context.fail("@PostgresChangeNotification requires @ChangeListener", element);
            return;
        }
        if (!validateChannel(element, context)) {
            return;
        }
        var entityType = ChangeListenerMethodUtils.resolveEntityType(element);
        if (entityType == null || !entityType.hasStereotype(MappedEntity.class)) {
            return;
        }
        var resolver = new SourcePersistentEntityResolver(context, new HashMap<>());
        var entity = resolver.apply(context.getClassElement(entityType.getName()).orElse(entityType));
        try {
            var reload = PostgresNotificationQuery.build(entity, new SourcePersistentEntityCriteriaBuilderImpl(resolver));
            element.annotate(POSTGRES_CHANGE_LISTENER_QUERY, annotation -> annotation.value(reload.query())
                .member("columns", reload.keys().stream().map(PostgresNotificationQuery.Key::column).toArray(String[]::new))
                .member("paths", reload.keys().stream().map(key -> key.path().getPath()).toArray(String[]::new)));
        } catch (IllegalArgumentException e) {
            context.fail("@PostgresChangeNotification " + e.getMessage(), element);
        }
    }

    /**
     * Validates literal channel names without changing their spelling or case.
     * Placeholder expressions are resolved and validated at runtime.
     *
     * @param element the annotated listener method
     * @param context the compilation visitor context used to report errors
     * @return {@code true} if the channel is supported
     */
    private static boolean validateChannel(MethodElement element, VisitorContext context) {
        String channel = element.stringValue(ANNOTATION, "channel").orElse("");
        if (channel.contains("${")) {
            return true;
        }
        if (channel.isBlank() || channel.indexOf('\0') >= 0 || channel.getBytes(StandardCharsets.UTF_8).length > 63) {
            context.fail("@PostgresChangeNotification channel must be non-blank, contain no NUL characters, "
                + "and occupy at most 63 UTF-8 bytes", element);
            return false;
        }
        return true;
    }
}
