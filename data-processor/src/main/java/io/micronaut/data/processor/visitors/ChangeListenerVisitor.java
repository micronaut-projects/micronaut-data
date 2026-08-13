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

import io.micronaut.data.intercept.annotation.ChangeListenerQuery;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.impl.SourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Generates the ROWID reload query metadata for Oracle change listeners.
 */
public final class ChangeListenerVisitor implements TypeElementVisitor<Object, Object> {
    private static final String CHANGE_LISTENER = "io.micronaut.data.jdbc.annotation.ChangeListener";
    private static final String QUERY_CHANGE_NOTIFICATION = "DCN_QUERY_CHANGE_NOTIFICATION";

    private final Map<String, SourcePersistentEntity> entityMap = new HashMap<>();

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

        ParameterElement[] parameters = element.getParameters();
        if (parameters.length != 1) {
            context.fail("@ChangeListener method must declare exactly one persistent entity argument", element);
            return;
        }
        if (!parameters[0].getType().hasStereotype(MappedEntity.class)) {
            context.fail("@ChangeListener method argument must be a persistent entity", element);
            return;
        }
        if (!validateRegistrationQuery(element.getAnnotationMetadata(), context, element)) {
            return;
        }

        Function<ClassElement, SourcePersistentEntity> entityResolver = new SourcePersistentEntityResolver(context, entityMap);

        SourcePersistentEntityCriteriaQuery<Object> query = new SourcePersistentEntityCriteriaBuilderImpl(entityResolver).createQuery();
        query.select(query.from(entityResolver.apply(parameters[0].getType())));
        QueryResult queryResult = Objects.requireNonNull(query.build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.ORACLE)));
        element.annotate(ChangeListenerQuery.class, builder -> builder
            .value(queryResult.getQuery() + " WHERE ROWID = ?")
            .member("entity", new AnnotationClassValue<>(parameters[0].getType().getName())));
    }

    private static boolean validateRegistrationQuery(AnnotationMetadata annotationMetadata,
                                                     VisitorContext context,
                                                     MethodElement element) {
        String select = annotationMetadata.stringValue(CHANGE_LISTENER, "select").orElse("*").trim();
        if (select.isEmpty()) {
            context.fail("@ChangeListener method must have a non-blank select value", element);
            return false;
        }
        String where = annotationMetadata.stringValue(CHANGE_LISTENER, "where").orElse("").trim();
        boolean queryChangeNotification = isQueryChangeNotificationEnabled(annotationMetadata);
        if (!queryChangeNotification && (!select.equals("*") || !where.isEmpty())) {
            context.fail("@ChangeListener method may specify select or where only when "
                + QUERY_CHANGE_NOTIFICATION + " is true", element);
            return false;
        }
        return true;
    }

    private static boolean isQueryChangeNotificationEnabled(AnnotationMetadata annotationMetadata) {
        Object properties = annotationMetadata.getValue(CHANGE_LISTENER, "properties").orElse(null);
        if (properties instanceof AnnotationValue<?>[] annotationValues) {
            for (AnnotationValue<?> property : annotationValues) {
                if (isQueryChangeNotificationEnabled(property)) {
                    return true;
                }
            }
        }
        if (properties instanceof Iterable<?> iterable) {
            for (Object property : iterable) {
                if (property instanceof AnnotationValue<?> annotationValue && isQueryChangeNotificationEnabled(annotationValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isQueryChangeNotificationEnabled(AnnotationValue<?> property) {
        return QUERY_CHANGE_NOTIFICATION.equals(property.stringValue("name").orElse(""))
            && Boolean.parseBoolean(property.stringValue("value").orElse("false"));
    }
}
