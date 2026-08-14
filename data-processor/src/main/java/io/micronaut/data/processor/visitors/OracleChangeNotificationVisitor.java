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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.intercept.annotation.OracleChangeListenerQuery;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery;
import io.micronaut.data.processor.model.criteria.impl.SourcePersistentEntityCriteriaBuilderImpl;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Validates Oracle notification configuration and generates Oracle ROWID reload query metadata.
 */
public final class OracleChangeNotificationVisitor implements TypeElementVisitor<Object, Object> {
    private static final String ORACLE_CHANGE_NOTIFICATION = "io.micronaut.data.jdbc.annotation.OracleChangeNotification";
    private static final String QUERY_CHANGE_NOTIFICATION = "DCN_QUERY_CHANGE_NOTIFICATION";
    private static final String NOTIFY_CHANGE_LAG = "DCN_NOTIFY_CHANGELAG";

    private final Map<String, SourcePersistentEntity> entityMap = new HashMap<>();

    @Override
    public int getOrder() {
        return MappedEntityVisitor.POSITION + 2;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!element.hasAnnotation(ORACLE_CHANGE_NOTIFICATION)) {
            return;
        }
        if (!element.hasAnnotation(ChangeListenerVisitor.CHANGE_LISTENER)) {
            context.fail("@OracleChangeNotification requires @ChangeListener", element);
            return;
        }
        ClassElement entityType = ChangeListenerMethodUtils.resolveEntityType(element);
        if (entityType == null || !entityType.hasStereotype(MappedEntity.class)) {
            return;
        }
        if (!validateRegistration(element.getAnnotationMetadata(), context, element)) {
            return;
        }

        Function<ClassElement, SourcePersistentEntity> entityResolver = new SourcePersistentEntityResolver(context, entityMap);
        SourcePersistentEntityCriteriaQuery<Object> query = new SourcePersistentEntityCriteriaBuilderImpl(entityResolver).createQuery();
        query.select(query.from(entityResolver.apply(entityType)));
        QueryResult queryResult = Objects.requireNonNull(query.build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.ORACLE)));
        element.annotate(OracleChangeListenerQuery.class, builder -> builder
            .value(queryResult.getQuery() + " WHERE ROWID = ?")
            .member("entity", new AnnotationClassValue<>(entityType.getName())));
    }

    private static boolean validateRegistration(AnnotationMetadata annotationMetadata,
                                                VisitorContext context,
                                                MethodElement element) {
        String select = annotationMetadata.stringValue(ORACLE_CHANGE_NOTIFICATION, "select").orElse("*").trim();
        if (select.isEmpty()) {
            context.fail("@OracleChangeNotification must have a non-blank select value", element);
            return false;
        }
        String where = annotationMetadata.stringValue(ORACLE_CHANGE_NOTIFICATION, "where").orElse("").trim();
        boolean queryChangeNotification = false;
        Object properties = annotationMetadata.getValue(ORACLE_CHANGE_NOTIFICATION, "properties").orElse(null);
        if (properties instanceof AnnotationValue<?>[] annotationValues) {
            for (AnnotationValue<?> property : annotationValues) {
                if (invalidChangeLag(property, context, element)) {
                    return false;
                }
                queryChangeNotification |= isEnabled(property, QUERY_CHANGE_NOTIFICATION);
            }
        } else if (properties instanceof Iterable<?> iterable) {
            for (Object property : iterable) {
                if (property instanceof AnnotationValue<?> annotationValue) {
                    if (invalidChangeLag(annotationValue, context, element)) {
                        return false;
                    }
                    queryChangeNotification |= isEnabled(annotationValue, QUERY_CHANGE_NOTIFICATION);
                }
            }
        }
        if (!queryChangeNotification && (!select.equals("*") || !where.isEmpty())) {
            context.fail("@OracleChangeNotification may specify select or where only when "
                + QUERY_CHANGE_NOTIFICATION + " is true", element);
            return false;
        }
        return true;
    }

    private static boolean invalidChangeLag(AnnotationValue<?> property,
                                            VisitorContext context,
                                            MethodElement element) {
        if (NOTIFY_CHANGE_LAG.equals(property.stringValue("name").orElse(""))
            && !"0".equals(property.stringValue("value").orElse("").trim())) {
            context.fail("@OracleChangeNotification requires " + NOTIFY_CHANGE_LAG
                + " to be 0 so row-level operation and ROWID details are available", element);
            return true;
        }
        return false;
    }

    private static boolean isEnabled(AnnotationValue<?> property, String name) {
        return name.equals(property.stringValue("name").orElse(""))
            && Boolean.parseBoolean(property.stringValue("value").orElse("false"));
    }
}
