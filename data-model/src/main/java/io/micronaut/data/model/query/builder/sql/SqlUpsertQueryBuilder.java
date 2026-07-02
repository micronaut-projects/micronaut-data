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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryOutParameterBinding;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.micronaut.data.annotation.GeneratedValue.Type.AUTO;
import static io.micronaut.data.annotation.GeneratedValue.Type.SEQUENCE;

final class SqlUpsertQueryBuilder {

    private static final char COMMA = ',';
    private static final char CLOSE_BRACKET = ')';
    private static final String BLANK_SPACE = " ";
    private static final String INSERT_INTO = "INSERT INTO ";
    private static final String R2DBC_REPO_ANNOTATION = "io.micronaut.data.r2dbc.annotation.R2dbcRepository";

    private final SqlQueryBuilder sqlQueryBuilder;
    private final Dialect dialect;

    SqlUpsertQueryBuilder(SqlQueryBuilder sqlQueryBuilder) {
        this.sqlQueryBuilder = sqlQueryBuilder;
        this.dialect = sqlQueryBuilder.getDialect();
    }

    QueryResult build(AnnotationMetadata repositoryMetadata, QueryBuilder.UpsertQueryDefinition definition) {
        PersistentEntity entity = definition.persistentEntity();
        if (sqlQueryBuilder.isJsonEntity(repositoryMetadata, entity)) {
            throw new IllegalStateException("Upsert is not supported for JSON entity representation: " + entity.getName());
        }
        if (definition.conflictProperties().isEmpty() && !entity.hasIdentity() && !entity.hasCompositeIdentity()) {
            throw new IllegalStateException("Upsert requires conflict properties or an identity for entity: " + entity.getName());
        }
        if (entity.hasVersion()) {
            throw new IllegalStateException("Upsert is not supported for versioned entity: " + entity.getName());
        }

        UpsertData data = buildUpsertData(entity, definition.conflictProperties());
        String tableName = sqlQueryBuilder.getTableName(entity);
        String query = switch (dialect) {
            case H2 -> buildH2Upsert(tableName, data);
            case MYSQL -> buildMySqlUpsert(tableName, data);
            case POSTGRES, SQLITE -> buildPostgresUpsert(tableName, data);
            case SQL_SERVER -> buildSqlServerUpsert(tableName, data);
            case ORACLE -> buildOracleUpsert(tableName, data);
            case ANSI -> buildAnsiUpsert(tableName, data);
        };

        List<QueryParameterBinding> parameterBindings = buildParameterBindings(data);

        UpsertReturningColumn returningColumn = findGeneratedIdReturningColumn(entity, definition);
        if (returningColumn == null) {
            if (dialect == Dialect.SQL_SERVER) {
                query = query + ";";
            }
            return QueryResult.of(query, parameterBindings);
        }

        if (dialect == Dialect.SQL_SERVER) {
            query = query + " OUTPUT inserted." + returningColumn.column() + ";";
        } else if (dialect == Dialect.ORACLE) {
            String outPlaceholder = sqlQueryBuilder.formatParameter(parameterBindings.size() + 1).name();
            query = query + " RETURNING " + returningColumn.column() + " INTO " + outPlaceholder;
            if (repositoryMetadata.hasStereotype(R2DBC_REPO_ANNOTATION)) {
                query = "BEGIN " + query + "; END;";
            }
        }
        List<QueryOutParameterBinding> outParameterBindings = buildOutParameterBindings(returningColumn);
        return QueryResult.of(query, Collections.emptyList(), parameterBindings, outParameterBindings, Collections.emptyMap());
    }

    @Nullable
    private UpsertReturningColumn findGeneratedIdReturningColumn(PersistentEntity entity, QueryBuilder.UpsertQueryDefinition definition) {
        if (!definition.returnGeneratedId() || (dialect != Dialect.ORACLE && dialect != Dialect.SQL_SERVER)) {
            return null;
        }
        List<UpsertReturningColumn> returningColumns = findGeneratedIdentityReturningColumns(entity);
        if (returningColumns.isEmpty()) {
            return null;
        }
        if (returningColumns.size() > 1) {
            String operation = dialect == Dialect.SQL_SERVER ? "SQL Server MERGE ... OUTPUT" : "Oracle MERGE ... RETURNING";
            throw new IllegalStateException(operation + " supports a single generated identity for entity: " + entity.getName());
        }
        return returningColumns.getFirst();
    }

    private List<UpsertReturningColumn> findGeneratedIdentityReturningColumns(PersistentEntity entity) {
        boolean escape = sqlQueryBuilder.shouldEscape(entity);
        NamingStrategy namingStrategy = sqlQueryBuilder.getNamingStrategy(entity);
        List<UpsertReturningColumn> columns = new ArrayList<>();
        for (PersistentProperty identity : entity.getIdentityProperties()) {
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property) -> {
                if (!SqlQueryBuilderUtils.isGeneratedProperty(property, associations)) {
                    return;
                }
                String columnName = sqlQueryBuilder.getMappedName(namingStrategy, associations, property);
                columns.add(new UpsertReturningColumn(escape ? sqlQueryBuilder.quote(columnName) : columnName, columnName, property.getDataType()));
            });
        }
        return columns;
    }

    private List<QueryParameterBinding> buildParameterBindings(UpsertData data) {
        if (dialect != Dialect.MYSQL) {
            return data.parameterBindings();
        }
        List<QueryParameterBinding> parameterBindings = new ArrayList<>(data.parameterBindings());
        for (UpsertColumn updateColumn : data.updateColumnsOrConflict()) {
            parameterBindings.add(sqlQueryBuilder.createParameterBinding(String.valueOf(parameterBindings.size() + 1), updateColumn.property(), updateColumn.path().toArray(new String[0])));
        }
        return parameterBindings;
    }

    private List<QueryOutParameterBinding> buildOutParameterBindings(UpsertReturningColumn returningColumn) {
        List<QueryOutParameterBinding> outBindings = new ArrayList<>(1);
        outBindings.add(new QueryOutParameterBinding() {
            @Override
            public String getName() {
                return returningColumn.name();
            }

            @Override
            public DataType getDataType() {
                return returningColumn.dataType();
            }
        });
        return outBindings;
    }

    private UpsertData buildUpsertData(PersistentEntity entity, List<String> conflictProperties) {
        boolean escape = sqlQueryBuilder.shouldEscape(entity);
        NamingStrategy namingStrategy = sqlQueryBuilder.getNamingStrategy(entity);
        List<UpsertColumn> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();
        List<QueryParameterBinding> parameterBindings = new ArrayList<>();
        List<String> conflictPropertyPaths = resolveUpsertConflictPropertyPaths(entity, conflictProperties);
        final String unescapedTableName = sqlQueryBuilder.getUnescapedTableName(entity);
        final String unescapedSchema = SqlQueryBuilderUtils.getSchemaName(entity);

        for (PersistentProperty prop : entity.getPersistentProperties()) {
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), prop, (associations, property) -> {
                if (SqlQueryBuilderUtils.isGeneratedProperty(property, associations)) {
                    return;
                }
                addUpsertColumn(columns, values, parameterBindings, namingStrategy, associations, property, escape, false, conflictPropertyPaths);
            });
        }

        boolean identityConflict = conflictProperties.isEmpty();
        for (PersistentProperty identity : entity.getIdentityProperties()) {
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property) -> {
                if (SqlQueryBuilderUtils.isGeneratedProperty(property, associations)) {
                    if (identityConflict) {
                        throw new IllegalStateException("Upsert requires a non-generated identity property: " + property.getName());
                    }
                    if (dialect != Dialect.SQL_SERVER && SqlQueryBuilderUtils.isNotForeign(associations) && isSequenceGeneratedProperty(property)) {
                        addGeneratedUpsertColumn(columns, namingStrategy, associations, property, escape, true, conflictPropertyPaths, sqlQueryBuilder.getSequenceStatement(unescapedSchema, unescapedTableName, property));
                    }
                    return;
                }
                addUpsertColumn(columns, values, parameterBindings, namingStrategy, associations, property, escape, true, conflictPropertyPaths);
            });
        }

        if (columns.isEmpty()) {
            throw new IllegalStateException("Upsert requires at least one bindable column for entity: " + entity.getName());
        }
        if (columns.stream().noneMatch(UpsertColumn::conflict)) {
            throw new IllegalStateException("Upsert requires at least one bindable conflict column for entity: " + entity.getName());
        }
        return new UpsertData(columns, parameterBindings);
    }

    private void addUpsertColumn(List<UpsertColumn> columns,
                                 List<String> values,
                                 List<QueryParameterBinding> parameterBindings,
                                 NamingStrategy namingStrategy,
                                 List<Association> associations,
                                 PersistentProperty property,
                                 boolean escape,
                                 boolean identity,
                                 List<String> conflictPropertyPaths) {
        sqlQueryBuilder.addWriteExpression(values, property);
        String key = String.valueOf(values.size());
        String[] path = sqlQueryBuilder.asStringPath(associations, property);
        parameterBindings.add(sqlQueryBuilder.createParameterBinding(key, property, path));

        String columnName = sqlQueryBuilder.getMappedName(namingStrategy, associations, property);
        if (escape) {
            columnName = sqlQueryBuilder.quote(columnName);
        }

        UpsertColumn column = new UpsertColumn(
            columnName,
            values.getLast(),
            "c" + sourceColumnCount(columns),
            true,
            property,
            List.of(path),
            identity,
            conflictPropertyPaths.contains(toPathString(path)));
        columns.add(column);
    }

    private void addGeneratedUpsertColumn(List<UpsertColumn> columns,
                                          NamingStrategy namingStrategy,
                                          List<Association> associations,
                                          PersistentProperty property,
                                          boolean escape,
                                          boolean identity,
                                          List<String> conflictPropertyPaths,
                                          String value) {
        String[] path = sqlQueryBuilder.asStringPath(associations, property);
        String columnName = sqlQueryBuilder.getMappedName(namingStrategy, associations, property);
        if (escape) {
            columnName = sqlQueryBuilder.quote(columnName);
        }

        UpsertColumn column = new UpsertColumn(
            columnName,
            value,
            "",
            false,
            property,
            List.of(path),
            identity,
            conflictPropertyPaths.contains(toPathString(path)));
        columns.add(column);
    }

    private int sourceColumnCount(List<UpsertColumn> columns) {
        return (int) columns.stream()
            .filter(UpsertColumn::sourceColumn)
            .count();
    }

    private boolean isSequenceGeneratedProperty(PersistentProperty property) {
        Optional<AnnotationValue<GeneratedValue>> generated = property.findAnnotation(GeneratedValue.class);
        if (generated.isEmpty()) {
            return false;
        }
        GeneratedValue.Type idGeneratorType = generated
            .flatMap(av -> av.enumValue(GeneratedValue.Type.class))
            .orElseGet(() -> sqlQueryBuilder.selectAutoStrategy(property));
        return idGeneratorType == SEQUENCE || (idGeneratorType == AUTO && sqlQueryBuilder.selectAutoStrategy(property) == SEQUENCE);
    }

    private List<String> resolveUpsertConflictPropertyPaths(PersistentEntity entity, List<String> conflictProperties) {
        List<String> conflictPropertyPaths = new ArrayList<>();
        if (conflictProperties.isEmpty()) {
            for (PersistentProperty identity : entity.getIdentityProperties()) {
                PersistentEntityUtils.traversePersistentProperties(
                    Collections.emptyList(),
                    identity,
                    (associations, property) -> conflictPropertyPaths.add(toPathString(associations, property)));
            }
            return conflictPropertyPaths;
        }
        for (String conflictProperty : conflictProperties) {
            if (StringUtils.isEmpty(conflictProperty) || StringUtils.isEmpty(conflictProperty.trim())) {
                throw new IllegalStateException("Upsert conflict property cannot be blank");
            }
            PersistentPropertyPath propertyPath;
            try {
                propertyPath = entity.getPropertyPath(conflictProperty);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid upsert conflict property path: " + conflictProperty, e);
            }
            if (propertyPath == null) {
                throw new IllegalStateException("Upsert conflict property does not exist: " + conflictProperty);
            }
            PersistentEntityUtils.traversePersistentProperties(propertyPath, (associations, property) -> {
                if (SqlQueryBuilderUtils.isGeneratedProperty(property, associations)) {
                    throw new IllegalStateException("Upsert requires a non-generated conflict property: " + conflictProperty);
                }
                String path = toPathString(associations, property);
                if (!conflictPropertyPaths.contains(path)) {
                    conflictPropertyPaths.add(path);
                }
            });
        }
        return conflictPropertyPaths;
    }

    private String toPathString(List<Association> associations, PersistentProperty property) {
        return toPathString(sqlQueryBuilder.asStringPath(associations, property));
    }

    private String toPathString(String[] path) {
        return String.join(".", path);
    }

    private String buildH2Upsert(String tableName, UpsertData data) {
        return "MERGE INTO " + tableName + " (" + data.columnNames() + ") KEY(" + data.conflictColumnNames() + ") VALUES (" + data.valueExpressions() + CLOSE_BRACKET;
    }

    private String buildMySqlUpsert(String tableName, UpsertData data) {
        List<UpsertColumn> updateColumns = data.updateColumnsOrConflict();
        return buildInsertStatement(tableName, data)
            + " ON DUPLICATE KEY UPDATE "
            + updateColumns.stream()
                .map(column -> column.column() + "=" + column.value())
                .collect(Collectors.joining(String.valueOf(COMMA)));
    }

    private String buildPostgresUpsert(String tableName, UpsertData data) {
        List<UpsertColumn> updateColumns = data.updateColumns();
        String conflict = buildInsertStatement(tableName, data) + " ON CONFLICT (" + data.conflictColumnNames() + CLOSE_BRACKET;
        if (updateColumns.isEmpty()) {
            return conflict + " DO NOTHING";
        }
        return conflict
            + " DO UPDATE SET "
            + updateColumns.stream()
                .map(column -> column.column() + "=EXCLUDED." + column.column())
                .collect(Collectors.joining(String.valueOf(COMMA)));
    }

    private String buildSqlServerUpsert(String tableName, UpsertData data) {
        return "MERGE INTO " + tableName + " WITH (HOLDLOCK) AS target "
            + "USING (VALUES (" + data.sourceValueExpressions() + ")) AS source (" + data.sourceColumns() + ") "
            + "ON " + upsertConflictPredicate(data)
            + upsertMatchedClause(data)
            + upsertInsertClause(data);
    }

    private String buildOracleUpsert(String tableName, UpsertData data) {
        String sourceSelect = data.columns().stream()
            .filter(UpsertColumn::sourceColumn)
            .map(column -> column.value() + BLANK_SPACE + column.source())
            .collect(Collectors.joining(String.valueOf(COMMA)));
        return "MERGE INTO " + tableName + " target "
            + "USING (SELECT " + sourceSelect + " FROM DUAL) source "
            + "ON (" + upsertConflictPredicate(data) + CLOSE_BRACKET
            + upsertMatchedClause(data)
            + upsertInsertClause(data);
    }

    private String buildAnsiUpsert(String tableName, UpsertData data) {
        return "MERGE INTO " + tableName + " target "
            + "USING (VALUES (" + data.sourceValueExpressions() + ")) source (" + data.sourceColumns() + ") "
            + "ON (" + upsertConflictPredicate(data) + CLOSE_BRACKET
            + upsertMatchedClause(data)
            + upsertInsertClause(data);
    }

    private String buildInsertStatement(String tableName, UpsertData data) {
        return INSERT_INTO + tableName + " (" + data.columnNames() + ") VALUES (" + data.valueExpressions() + CLOSE_BRACKET;
    }

    private String upsertConflictPredicate(UpsertData data) {
        return data.conflictColumns().stream()
            .map(column -> "target." + column.column() + "=source." + column.source())
            .collect(Collectors.joining(" AND "));
    }

    private String upsertMatchedClause(UpsertData data) {
        List<UpsertColumn> updateColumns = data.updateColumns();
        if (updateColumns.isEmpty()) {
            return "";
        }
        return " WHEN MATCHED THEN UPDATE SET "
            + updateColumns.stream()
                .map(column -> "target." + column.column() + "=source." + column.source())
                .collect(Collectors.joining(String.valueOf(COMMA)));
    }

    private String upsertInsertClause(UpsertData data) {
        return " WHEN NOT MATCHED THEN INSERT (" + data.columnNames() + ") VALUES ("
            + data.columns().stream()
                .map(column -> column.sourceColumn() ? "source." + column.source() : column.value())
                .collect(Collectors.joining(String.valueOf(COMMA)))
            + CLOSE_BRACKET;
    }

    private record UpsertData(List<UpsertColumn> columns,
                              List<QueryParameterBinding> parameterBindings) {

        private String columnNames() {
            return columns.stream()
                .map(UpsertColumn::column)
                .collect(Collectors.joining(String.valueOf(COMMA)));
        }

        private String valueExpressions() {
            return columns.stream()
                .map(UpsertColumn::value)
                .collect(Collectors.joining(String.valueOf(COMMA)));
        }

        private String sourceValueExpressions() {
            return columns.stream()
                .filter(UpsertColumn::sourceColumn)
                .map(UpsertColumn::value)
                .collect(Collectors.joining(String.valueOf(COMMA)));
        }

        private String sourceColumns() {
            return columns.stream()
                .filter(UpsertColumn::sourceColumn)
                .map(UpsertColumn::source)
                .collect(Collectors.joining(String.valueOf(COMMA)));
        }

        private List<UpsertColumn> conflictColumns() {
            return columns.stream()
                .filter(UpsertColumn::conflict)
                .toList();
        }

        private String conflictColumnNames() {
            return conflictColumns().stream()
                .map(UpsertColumn::column)
                .collect(Collectors.joining(String.valueOf(COMMA)));
        }

        private List<UpsertColumn> updateColumns() {
            return columns.stream()
                .filter(column -> !column.identity() && !column.conflict())
                .toList();
        }

        private List<UpsertColumn> updateColumnsOrConflict() {
            List<UpsertColumn> updateColumns = updateColumns();
            return updateColumns.isEmpty() ? List.of(conflictColumns().getFirst()) : updateColumns;
        }
    }

    private record UpsertColumn(String column,
                                String value,
                                String source,
                                boolean sourceColumn,
                                PersistentProperty property,
                                List<String> path,
                                boolean identity,
                                boolean conflict) {
    }

    private record UpsertReturningColumn(String column,
                                         String name,
                                         DataType dataType) {
    }
}
