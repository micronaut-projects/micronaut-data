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
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Processes {@code @OracleChangeNotification} methods during compilation.
 *
 * <p>The visitor verifies that the method is also a {@code @ChangeListener}, validates
 * Oracle registration settings and query-change-notification select columns against the
 * entity's physical mapping using Oracle Database identifier rules, and generates
 * an internal {@link OracleChangeListenerQuery} annotation containing the query used to
 * reload the mapped entity by its reported Oracle {@code ROWID}.</p>
 *
 * <p>This visitor performs compile-time processing only. It does not open a datasource
 * connection or create an Oracle Database notification registration.</p>
 */
public final class OracleChangeNotificationVisitor implements TypeElementVisitor<Object, Object> {
    private static final String ORACLE_CHANGE_NOTIFICATION = "io.micronaut.data.jdbc.annotation.OracleChangeNotification";
    private static final String QUERY_CHANGE_NOTIFICATION = "DCN_QUERY_CHANGE_NOTIFICATION";
    private static final String NOTIFY_CHANGE_LAG = "DCN_NOTIFY_CHANGELAG";
    private static final String NOTIFICATION_TIMEOUT = "NTF_TIMEOUT";

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
        if (!element.hasStereotype(ORACLE_CHANGE_NOTIFICATION)) {
            return;
        }
        if (!element.hasStereotype(ChangeListenerVisitor.CHANGE_LISTENER)) {
            context.fail("@OracleChangeNotification requires @ChangeListener", element);
            return;
        }
        ClassElement entityType = ChangeListenerMethodUtils.resolveEntityType(element);
        if (entityType == null || !entityType.hasStereotype(MappedEntity.class)) {
            return;
        }
        ClassElement resolvedEntityType = context.getClassElement(entityType.getName()).orElse(entityType);
        Function<ClassElement, SourcePersistentEntity> entityResolver = new SourcePersistentEntityResolver(context, entityMap);
        SourcePersistentEntity persistentEntity = entityResolver.apply(resolvedEntityType);
        if (!validateRegistration(element.getAnnotationMetadata(), context, element, persistentEntity)) {
            return;
        }

        var criteriaBuilder = new SourcePersistentEntityCriteriaBuilderImpl(entityResolver);
        SourcePersistentEntityCriteriaQuery<Object> query = criteriaBuilder.createQuery();
        query.select(query.from(persistentEntity));
        query.where(criteriaBuilder.equal(
            criteriaBuilder.function("ROWID", String.class), criteriaBuilder.parameter(String.class, "rowId")));
        QueryResult queryResult = Objects.requireNonNull(query.build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.ORACLE)));
        List<QueryParameterBinding> bindings = queryResult.getParameterBindings();
        // The runtime loader supplies only the notification ROWID; no other query parameters are available.
        if (bindings.size() != 1 || bindings.get(0).getDataType() != DataType.STRING) {
            context.fail("@ChangeListener ROWID reload query requires exactly one ROWID parameter; "
                + "parameterized entity @Where clauses are not supported", element);
            return;
        }
        element.annotate(OracleChangeListenerQuery.class, builder -> builder
            .value(queryResult.getQuery())
            .member("entity", new AnnotationClassValue<>(resolvedEntityType.getName())));
    }

    private static boolean validateRegistration(AnnotationMetadata annotationMetadata,
                                                VisitorContext context,
                                                MethodElement element,
                                                SourcePersistentEntity persistentEntity) {
        String select = annotationMetadata.stringValue(ORACLE_CHANGE_NOTIFICATION, "select").orElse("*").trim();
        if (select.isEmpty()) {
            context.fail("@OracleChangeNotification must have a non-blank select value", element);
            return false;
        }
        String where = annotationMetadata.stringValue(ORACLE_CHANGE_NOTIFICATION, "where").orElse("").trim();
        int timeoutSeconds = annotationMetadata.intValue(ORACLE_CHANGE_NOTIFICATION, "timeoutSeconds").orElse(0);
        int leadTimeSeconds = annotationMetadata.intValue(ORACLE_CHANGE_NOTIFICATION, "renewalLeadTimeSeconds").orElse(60);
        String renewal = annotationMetadata.stringValue(ORACLE_CHANGE_NOTIFICATION, "renewal").orElse("NONE");
        if (timeoutSeconds < 0) {
            context.fail("@OracleChangeNotification requires timeoutSeconds to be at least 0", element);
            return false;
        }
        if (!"NONE".equals(renewal) && timeoutSeconds == 0) {
            context.fail("@OracleChangeNotification requires timeoutSeconds to be greater than 0", element);
            return false;
        }
        if ("OVERLAPPING".equals(renewal) && (leadTimeSeconds <= 0 || leadTimeSeconds >= timeoutSeconds)) {
            context.fail("@OracleChangeNotification requires renewalLeadTimeSeconds to be greater than 0 "
                + "and less than timeoutSeconds", element);
            return false;
        }
        boolean queryChangeNotification = false;
        Object properties = annotationMetadata.getValue(ORACLE_CHANGE_NOTIFICATION, "properties").orElse(null);
        if (properties instanceof AnnotationValue<?>[] annotationValues) {
            for (AnnotationValue<?> property : annotationValues) {
                if (invalidChangeLag(property, context, element)) {
                    return false;
                }
                if (invalidTimeoutProperty(property, context, element)) {
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
                    if (invalidTimeoutProperty(annotationValue, context, element)) {
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
        if (queryChangeNotification && !validateSelect(select, persistentEntity, context, element)) {
            return false;
        }
        return true;
    }

    private static boolean validateSelect(String select,
                                          SourcePersistentEntity persistentEntity,
                                          VisitorContext context,
                                          MethodElement element) {
        if (select.equals("*")) {
            return true;
        }
        Set<String> mappedColumns = mappedColumns(persistentEntity);
        for (String selection : select.split(",", -1)) {
            String column = selection.trim();
            if (!matchesMappedColumn(column, mappedColumns)) {
                context.fail("@OracleChangeNotification select must be '*' or a comma-separated list of mapped columns; "
                    + "unsupported selection [" + column + "]", element);
                return false;
            }
        }
        return true;
    }

    private static Set<String> mappedColumns(PersistentEntity persistentEntity) {
        Set<String> columns = new HashSet<>();
        PersistentEntityUtils.traversePersistentProperties(persistentEntity, (associations, property) ->
            columns.add(persistentEntity.getNamingStrategy().mappedName(associations, property).toUpperCase(Locale.ENGLISH)));
        PersistentEntity parentEntity = persistentEntity.getParentEntity();
        if (parentEntity != null) {
            columns.addAll(mappedColumns(parentEntity));
        }
        return columns;
    }

    private static boolean matchesMappedColumn(String column, Set<String> mappedColumns) {
        if (column.length() > 2 && column.charAt(0) == '"' && column.charAt(column.length() - 1) == '"') {
            // Quoted Oracle identifiers are case-sensitive; the Oracle query builder renders
            // mapped column names in upper case inside its identifier quotes.
            return mappedColumns.contains(column.substring(1, column.length() - 1));
        }
        return column.matches("[A-Za-z][A-Za-z0-9_$#]*")
            && mappedColumns.contains(column.toUpperCase(Locale.ENGLISH));
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

    private static boolean invalidTimeoutProperty(AnnotationValue<?> property,
                                                  VisitorContext context,
                                                  MethodElement element) {
        if (NOTIFICATION_TIMEOUT.equals(property.stringValue("name").orElse(""))) {
            context.fail("@OracleChangeNotification must configure Oracle registration timeout with timeoutSeconds", element);
            return true;
        }
        return false;
    }

    private static boolean isEnabled(AnnotationValue<?> property, String name) {
        return name.equals(property.stringValue("name").orElse(""))
            && Boolean.parseBoolean(property.stringValue("value").orElse("false"));
    }
}
