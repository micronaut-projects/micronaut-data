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

    /**
     * Validates Oracle notification settings and generates the entity reload query at compilation time.
     *
     * @param element the annotated listener method
     * @param context the compilation visitor context used to report errors and add generated metadata
     */
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

    /**
     * Validates notification mode, Oracle properties, and select configuration.
     *
     * @param annotationMetadata the listener method's annotation metadata
     * @param context            the compilation visitor context used to report errors
     * @param element            the annotated listener method
     * @param persistentEntity   the entity mapping used to validate selected columns
     * @return {@code true} if the registration configuration is valid
     */
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
        boolean queryChangeNotification = false;
        var properties = annotationMetadata.findAnnotation(ORACLE_CHANGE_NOTIFICATION)
            .map(annotation -> annotation.getAnnotations("properties"))
            .orElse(List.of());
        for (AnnotationValue<?> property : properties) {
            if (invalidOracleProperty(property, context, element)) {
                return false;
            }
            if (QUERY_CHANGE_NOTIFICATION.equals(property.stringValue("name").orElse(""))) {
                // The runtime factory stores properties in order, so the last value wins.
                queryChangeNotification = Boolean.parseBoolean(property.stringValue("value").orElse(""));
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

    /**
     * Ensures a QRCN select list contains only {@code *} or mapped physical column identifiers.
     *
     * @param select           the configured select fragment
     * @param persistentEntity the entity mapping used to resolve physical column names
     * @param context          the compilation visitor context used to report errors
     * @param element          the annotated listener method
     * @return {@code true} if the select list is supported
     */
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

    /**
     * Collects mapped physical columns from the entity and its persistent parent mappings.
     *
     * @param persistentEntity the entity mapping to inspect
     * @return canonical unquoted column names as rendered by the Oracle query builder
     */
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

    /**
     * Matches a select identifier using Oracle's exact quoted and case-insensitive unquoted rules.
     *
     * @param column        the configured identifier
     * @param mappedColumns the canonical mapped column names
     * @return {@code true} if the identifier refers to a mapped column
     */
    private static boolean matchesMappedColumn(String column, Set<String> mappedColumns) {
        if (column.length() > 2 && column.charAt(0) == '"' && column.charAt(column.length() - 1) == '"') {
            // Quoted Oracle identifiers are case-sensitive; the Oracle query builder renders
            // mapped column names in upper case inside its identifier quotes.
            return mappedColumns.contains(column.substring(1, column.length() - 1));
        }
        return column.matches("[A-Za-z][A-Za-z0-9_$#]*")
            && mappedColumns.contains(column.toUpperCase(Locale.ENGLISH));
    }

    /**
     * Reports unsupported or conflicting Oracle options used by the listener registration.
     *
     * @param property the configured Oracle property
     * @param context  the compilation visitor context used to report errors
     * @param element  the annotated listener method
     * @return {@code true} when the property is invalid
     */
    private static boolean invalidOracleProperty(AnnotationValue<?> property,
                                                 VisitorContext context,
                                                 MethodElement element) {
        String name = property.stringValue("name").orElse("");
        String value = property.stringValue("value").orElse("");
        String error = null;
        if (name.isBlank()) {
            error = "has an Oracle property with a blank name";
        } else if (NOTIFY_CHANGE_LAG.equals(name) && !"0".equals(value.trim())) {
            error = "requires " + name + " to be 0 so row-level operation and ROWID details are available";
        } else if ("DCN_NOTIFY_ROWIDS".equals(name) && !"true".equalsIgnoreCase(value)) {
            error = "requires " + name + " to be true so row-level operation and ROWID details are available";
        } else if (NOTIFICATION_TIMEOUT.equals(name)) {
            error = NOTIFICATION_TIMEOUT + ": registration timeouts are not supported for application-lifetime listeners";
        } else if ("DCN_CLIENT_INIT_REGID".equals(name)) {
            error = name + ": reusing an existing reliable DCN registration is not supported";
        } else if ("NTF_GROUPING_CLASS".equals(name) && !"NTF_GROUPING_CLASS_NONE".equals(value)) {
            error = name + ": notification grouping is not supported";
        } else if ("NTF_GROUPING_VALUE".equals(name)
            || "NTF_GROUPING_TYPE".equals(name)
            || "NTF_GROUPING_REPEAT_TIME".equals(name)
            || "NTF_GROUPING_START_TIME".equals(name)) {
            error = name + ": notification grouping is not supported";
        } else if ("DCN_PULL_NOTIFICATIONS".equals(name) && !"false".equalsIgnoreCase(value)) {
            error = name + " [" + value + "] is not supported because AQ pull delivery does not invoke the listener callback";
        } else if ("DCN_PULL_QUEUE_NAME".equals(name)) {
            error = name + " is not supported because AQ pull delivery does not invoke the listener callback";
        }
        if (error != null) {
            context.fail("@OracleChangeNotification " + error, element);
            return true;
        }
        return false;
    }

}
