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
package io.micronaut.data.jdbc.notification.oracle;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.jdbc.annotation.OracleChangeNotification;
import io.micronaut.data.jdbc.notification.ChangeListenerMethod;
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder;
import io.micronaut.inject.ExecutableMethod;
import oracle.jdbc.OracleConnection;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Builds the Oracle-specific runtime definition for a discovered change listener.
 *
 * <p>The factory resolves the entity's mapped Oracle table, reads the compile-time generated
 * {@code ROWID} reload query (or builds it for inherited methods without generated metadata),
 * copies the annotation's registration properties, defaults to a
 * client-initiated connection, requests row identifiers, and defaults the registration timeout to
 * zero when no timeout is configured. It also builds the query associated with the registration.</p>
 *
 * <p>The annotation processor validates annotation-level listener settings at compile time.
 * Inherited methods from separately compiled classes may not have generated query metadata.</p>
 */
final class OracleChangeListenerDefinitionFactory {
    private final JdbcRepositoryOperations operations;

    OracleChangeListenerDefinitionFactory(JdbcRepositoryOperations operations) {
        this.operations = operations;
    }

    /**
     * Creates the Oracle runtime definition for one discovered listener method.
     *
     * @param listenerMethod the validated listener method and entity type
     * @return its table mapping, registration query, reload query, and properties
     */
    OracleChangeListenerDefinition create(ChangeListenerMethod listenerMethod) {
        ExecutableMethod<?, ?> method = listenerMethod.method();
        AnnotationValue<OracleChangeNotification> notification = method.getAnnotation(OracleChangeNotification.class);
        if (notification == null) {
            throw invalidChangeListener(method, "requires @OracleChangeNotification for an Oracle datasource");
        }
        Argument<?> entityArgument = listenerMethod.entityArgument();
        RuntimePersistentEntity<?> persistentEntity = operations.getEntity(entityArgument.getType());
        SqlQueryBuilder queryBuilder = new SqlQueryBuilder(Dialect.ORACLE);
        OracleTableIdentifier tableIdentifier = OracleTableIdentifier.parse(
            queryBuilder.getTableName(persistentEntity)
        );
        String reloadQuery = method.stringValue(OracleChangeListenerQuery.class)
            .orElseGet(() -> reloadQuery(method, persistentEntity, queryBuilder));
        Properties properties = registrationProperties(notification);
        return new OracleChangeListenerDefinition(
            listenerMethod.beanDefinition(),
            method,
            tableIdentifier,
            registrationQuery(notification, tableIdentifier),
            new OracleChangeListenerEntityLoader<>(operations, entityArgument.getType(), reloadQuery),
            properties
        );
    }

    /**
     * Builds the same mapped ROWID query as the visitor when an inherited listener was not visited.
     * The loader can bind only one string ROWID, so filtered or parameterized entity mappings must
     * be rejected here as well as during compilation.
     *
     * @param method the listener method used in diagnostics
     * @param persistentEntity the entity mapping
     * @param queryBuilder the Oracle query builder
     * @return the ROWID reload query
     */
    private static String reloadQuery(ExecutableMethod<?, ?> method,
                                      RuntimePersistentEntity<?> persistentEntity,
                                      SqlQueryBuilder queryBuilder) {
        if (!persistentEntity.getAnnotationMetadata().getAnnotationValuesByType(Where.class).isEmpty()) {
            throw invalidChangeListener(method, "does not support entity-level @Where clauses: "
                + "they filter ROWID reloads without filtering the registration query");
        }
        var criteriaBuilder = new RuntimeCriteriaBuilder();
        var query = criteriaBuilder.createQuery();
        query.select(query.from(persistentEntity));
        query.where(criteriaBuilder.equal(
            criteriaBuilder.function("ROWID", String.class), criteriaBuilder.parameter(String.class, "rowId")));
        var result = Objects.requireNonNull(query.build(AnnotationMetadata.EMPTY_METADATA, queryBuilder));
        if (result.getQuery().contains("${")) {
            throw invalidChangeListener(method, "ROWID reload query must not contain configuration placeholders; "
                + "use literal entity mappings");
        }
        var bindings = result.getParameterBindings();
        if (bindings.size() != 1 || bindings.getFirst().getDataType() != DataType.STRING) {
            throw invalidChangeListener(method, "ROWID reload query requires exactly one ROWID parameter");
        }
        return result.getQuery();
    }

    /**
     * Copies annotation properties, then applies required framework defaults.
     *
     * @param notification the listener's Oracle notification annotation
     * @return the registration properties passed to the JDBC driver
     */
    private static Properties registrationProperties(AnnotationValue<OracleChangeNotification> notification) {
        Properties properties = new Properties();
        List<AnnotationValue<OracleChangeNotification.Property>> propertyValues = notification
            .getAnnotations("properties", OracleChangeNotification.Property.class);
        for (AnnotationValue<OracleChangeNotification.Property> property : propertyValues) {
            String name = property.stringValue("name").orElse("");
            String value = property.stringValue("value").orElse("");
            properties.setProperty(name, value);
        }
        properties.putIfAbsent(OracleConnection.DCN_CLIENT_INIT_CONNECTION, "true");
        properties.setProperty(OracleConnection.DCN_NOTIFY_ROWIDS, "true");
        properties.putIfAbsent(OracleConnection.NTF_TIMEOUT, "0");
        return properties;
    }

    /**
     * Builds the table registration query from the mapped table and configured query fragments.
     *
     * @param notification    the listener's Oracle notification annotation
     * @param tableIdentifier the entity's mapped Oracle table
     * @return the SQL statement associated with the Oracle registration
     */
    private static String registrationQuery(AnnotationValue<OracleChangeNotification> notification,
                                            OracleTableIdentifier tableIdentifier) {
        String select = notification.stringValue("select").orElse("*").trim();
        String where = notification.stringValue("where").orElse("").trim();
        return "SELECT " + select + " FROM " + tableIdentifier.sqlName() + (where.isEmpty() ? "" : " WHERE " + where);
    }

    private static IllegalStateException invalidChangeListener(ExecutableMethod<?, ?> method, String message) {
        return new IllegalStateException("@ChangeListener method [" + method.getDescription(true) + "] " + message);
    }
}
