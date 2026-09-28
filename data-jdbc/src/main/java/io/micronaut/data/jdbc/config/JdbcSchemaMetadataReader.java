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
package io.micronaut.data.jdbc.config;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.IdentifierNamingStrategy;
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils;
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlIndexMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlJsonViewMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads the schema metadata used by the schema validation using JDBC {@link DatabaseMetaData}.
 * <p>
 * The metadata is read for the whole schema with as few database calls as possible and kept in memory:
 * the tables and their columns are read with a single call. Primary keys and indexes are
 * read with a single call for all the tables when the driver supports it (a null table name),
 * otherwise with a call per table. Entities can also be mapped to views, since the tables are resolved from the columns.
 *
 * @author radovanradic
 * @since 5.3.0
 */
@Internal
final class JdbcSchemaMetadataReader {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcSchemaMetadataReader.class);

    private static final String MATCH_ALL = "%";

    private static final String JSON_DUALITY_VIEWS_QUERY = """
        SELECT VIEW_NAME, ROOT_TABLE_NAME, ALLOW_INSERT, ALLOW_UPDATE, ALLOW_DELETE, STATUS
        FROM ALL_JSON_DUALITY_VIEWS WHERE VIEW_OWNER = ?""";
    private static final String JSON_DUALITY_VIEW_TABLES_QUERY = """
        SELECT VIEW_NAME, TABLE_NAME
        FROM ALL_JSON_DUALITY_VIEW_TABS WHERE VIEW_OWNER = ?""";
    private static final String JSON_DUALITY_VIEW_COLUMNS_QUERY = """
        SELECT VIEW_NAME, TABLE_NAME, COLUMN_NAME, JSON_KEY_NAME
        FROM ALL_JSON_DUALITY_VIEW_TAB_COLS WHERE VIEW_OWNER = ? AND JSON_KEY_NAME IS NOT NULL""";

    private final Connection connection;
    private final DatabaseMetaData metaData;
    private final Dialect dialect;
    private final IdentifierNamingStrategy namingStrategy;

    JdbcSchemaMetadataReader(Connection connection, Dialect dialect) throws SQLException {
        this.connection = connection;
        this.metaData = connection.getMetaData();
        this.dialect = dialect;
        this.namingStrategy = getIdentifierNamingStrategy(metaData);
    }

    /**
     * Resolves the schema name as stored in the database, the same way as the generated SQL refers to it:
     * an escaped (quoted) name is stored as declared, otherwise the database stores the name in its identifier case.
     *
     * @param schema The schema name as declared in the mapping
     * @param escape Whether the mapping escapes the names
     * @return The schema name as stored in the database, null for the connection default schema
     */
    @Nullable String resolveSchema(@Nullable String schema, boolean escape) {
        if (StringUtils.isEmpty(schema)) {
            return null;
        }
        return escape ? schema : namingStrategy.apply(schema);
    }

    /**
     * Reads the metadata of the given tables in the schema.
     *
     * @param schema The schema name as stored in the database (see {@link #resolveSchema(String, boolean)}), null for the connection default schema
     * @param wantedTableNames The lower case names of the tables to read, other tables are skipped
     * @return The schema tables
     * @throws SQLException If reading the metadata fails
     */
    SchemaTables readTables(@Nullable String schema, Set<String> wantedTableNames) throws SQLException {
        String catalog = connection.getCatalog();
        if (schema == null) {
            String currentSchema = dialect == Dialect.MYSQL ? null : connection.getSchema();
            return readTables(apply(catalog), apply(currentSchema), wantedTableNames);
        }
        return dialect == Dialect.MYSQL
            ? readTables(schema, null, wantedTableNames)
            : readTables(apply(catalog), schema, wantedTableNames);
    }

    /**
     * Reads the names of the sequences of the schema.
     *
     * @param query The query selecting the sequence names, see {@link io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator#getSequenceNamesQuery()}
     * @param schema The schema (database for MySQL) as stored in the database
     * @return The lower case sequence names
     * @throws SQLException If the query fails
     */
    Set<String> readSequenceNames(String query, @Nullable String schema) throws SQLException {
        Set<String> sequenceNames = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, schema);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String name = resultSet.getString(1);
                    if (name != null) {
                        sequenceNames.add(name.toLowerCase(Locale.ENGLISH));
                    }
                }
            }
        }
        return sequenceNames;
    }

    /**
     * Reads the full column type definitions of the schema tables with a single query, see
     * {@link io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator#getColumnTypeDefinitionsQuery()}.
     * Failures are ignored and the type definitions are not verified.
     *
     * @param query The query selecting the table names, column names and type definitions
     * @param schemaTables The schema tables to populate
     */
    void readColumnTypeDefinitions(String query, SchemaTables schemaTables) {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, schemaTables.schema());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString(1);
                    String columnName = resultSet.getString(2);
                    String typeDefinition = resultSet.getString(3);
                    SqlTableMetadata table = tableName == null ? null : schemaTables.tables().get(tableName.toLowerCase(Locale.ENGLISH));
                    if (table != null && columnName != null && typeDefinition != null) {
                        table.setColumnTypeDefinition(columnName, typeDefinition);
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to read the column type definitions of schema [{}], skipping their validation: {}", schemaTables.schema(), e.getMessage(), e);
            }
        }
    }

    /**
     * Reads the Oracle JSON relational duality views of the schema.
     *
     * @param viewSchema The schema (view owner) as stored in the database, null for the connection default schema
     * @return The views by lower case view name
     * @throws SQLException If the views cannot be read
     */
    Map<String, SqlJsonViewMetadata> readJsonDualityViews(@Nullable String viewSchema) throws SQLException {
        String schema = viewSchema == null ? connection.getSchema() : viewSchema;
        Map<String, JsonViewBuilder> views = new LinkedHashMap<>();
        query(JSON_DUALITY_VIEWS_QUERY, schema, resultSet -> {
            String viewName = resultSet.getString(1);
            views.put(viewName.toLowerCase(Locale.ENGLISH), new JsonViewBuilder(viewName, resultSet.getString(2),
                resultSet.getBoolean(3), resultSet.getBoolean(4), resultSet.getBoolean(5), resultSet.getString(6)));
        });
        if (views.isEmpty()) {
            return Map.of();
        }
        query(JSON_DUALITY_VIEW_TABLES_QUERY, schema, resultSet -> {
            JsonViewBuilder view = views.get(resultSet.getString(1).toLowerCase(Locale.ENGLISH));
            if (view != null) {
                view.tables.add(resultSet.getString(2));
            }
        });
        query(JSON_DUALITY_VIEW_COLUMNS_QUERY, schema, resultSet -> {
            JsonViewBuilder view = views.get(resultSet.getString(1).toLowerCase(Locale.ENGLISH));
            if (view != null) {
                view.fields.add(new SqlJsonViewMetadata.Field(resultSet.getString(2), resultSet.getString(4), resultSet.getString(3)));
            }
        });
        Map<String, SqlJsonViewMetadata> result = new LinkedHashMap<>(views.size());
        views.forEach((key, view) -> result.put(key, new SqlJsonViewMetadata(view.name, view.rootTable, view.allowInsert,
            view.allowUpdate, view.allowDelete, view.status, view.tables, view.fields)));
        return result;
    }

    private void query(String sql, @Nullable String parameter, RowReader rowReader) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rowReader.read(resultSet);
                }
            }
        }
    }

    private SchemaTables readTables(@Nullable String catalog,
                                    @Nullable String schema,
                                    Set<String> wantedTableNames) throws SQLException {
        Map<String, SqlTableMetadata> tables = new LinkedHashMap<>();
        String resolvedSchema = dialect == Dialect.MYSQL ? catalog : schema;
        // The tables and their columns are read with a single call, the tables (and views) are the owners of the columns
        try (ResultSet resultSet = metaData.getColumns(catalog, schema, null, MATCH_ALL)) {
            while (resultSet.next()) {
                String tableName = resultSet.getString(SqlSchemaUtils.TABLE_NAME_COLUMN);
                String tableNameLowerCase = tableName.toLowerCase(Locale.ENGLISH);
                if (!wantedTableNames.contains(tableNameLowerCase)) {
                    // No need to read columns of the table which does not have mapped entity
                    continue;
                }
                String tableCatalog = resultSet.getString(SqlSchemaUtils.TABLE_CATALOG_COLUMN);
                String tableSchema = resultSet.getString(SqlSchemaUtils.TABLE_SCHEMA_COLUMN);
                SqlTableMetadata table = tables.get(tableNameLowerCase);
                if (table == null) {
                    table = new SqlTableMetadata(tableCatalog, tableSchema, tableName);
                    tables.put(tableNameLowerCase, table);
                    resolvedSchema = dialect == Dialect.MYSQL ? tableCatalog : tableSchema;
                } else if (!Objects.equals(table.getCatalog(), tableCatalog) || !Objects.equals(table.getSchema(), tableSchema)) {
                    // A table with the same name in another schema (the schema pattern can match more schemas)
                    continue;
                }
                addColumn(table, resultSet);
            }
        }
        if (tables.isEmpty()) {
            return new SchemaTables(resolvedSchema, tables);
        }
        readPrimaryKeys(tables);
        readIndexes(tables);
        return new SchemaTables(resolvedSchema, tables);
    }

    private static void addColumn(SqlTableMetadata table, ResultSet resultSet) throws SQLException {
        String columnName = resultSet.getString(SqlSchemaUtils.COLUMN_NAME_COLUMN);
        int columnType = resultSet.getInt(SqlSchemaUtils.DATA_TYPE_COLUMN);
        String typeName = resultSet.getString(SqlSchemaUtils.TYPE_NAME_COLUMN);
        int columnSize = resultSet.getInt(SqlSchemaUtils.COLUMN_SIZE_COLUMN);
        // The number of fractional digits, null (read as 0) for data types where it is not applicable
        int decimalDigits = resultSet.getInt(SqlSchemaUtils.DECIMAL_DIGITS_COLUMN);
        // Unknown nullability is treated as nullable
        boolean nullable = resultSet.getInt(SqlSchemaUtils.NULLABLE_COLUMN) != DatabaseMetaData.columnNoNulls;
        table.addColumn(new SqlColumnMetadata(columnName, columnType, typeName, columnSize, decimalDigits, nullable));
    }

    private void readPrimaryKeys(Map<String, SqlTableMetadata> tables) {
        Map<String, Map<Integer, String>> primaryKeys = new HashMap<>();
        Set<String> readTables = readTablesMetadata("primary keys", tables,
            SqlSchemaUtils.TABLE_CATALOG_COLUMN, SqlSchemaUtils.TABLE_SCHEMA_COLUMN, SqlSchemaUtils.TABLE_NAME_COLUMN,
            (catalog, schema, table) -> metaData.getPrimaryKeys(catalog, schema, table),
            (tableKey, resultSet) -> primaryKeys.computeIfAbsent(tableKey, k -> new TreeMap<>())
                .put(resultSet.getInt("KEY_SEQ"), resultSet.getString(SqlSchemaUtils.COLUMN_NAME_COLUMN)),
            primaryKeys::clear);
        for (String tableKey : readTables) {
            Objects.requireNonNull(tables.get(tableKey)).setPrimaryKeyColumns(new ArrayList<>(primaryKeys.getOrDefault(tableKey, Map.of()).values()));
        }
    }

    private void readIndexes(Map<String, SqlTableMetadata> tables) {
        Map<String, Map<String, IndexColumns>> indexes = new HashMap<>();
        // approximate = true, some drivers (Oracle) would otherwise compute the table statistics
        Set<String> readTables = readTablesMetadata("indexes", tables,
            SqlSchemaUtils.TABLE_CATALOG_COLUMN, SqlSchemaUtils.TABLE_SCHEMA_COLUMN, SqlSchemaUtils.TABLE_NAME_COLUMN,
            (catalog, schema, table) -> metaData.getIndexInfo(catalog, schema, table, false, true),
            (tableKey, resultSet) -> {
                String indexName = resultSet.getString("INDEX_NAME");
                if (indexName == null || resultSet.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    return;
                }
                boolean unique = !resultSet.getBoolean("NON_UNIQUE");
                IndexColumns index = indexes.computeIfAbsent(tableKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(indexName, name -> new IndexColumns(unique, new TreeMap<>()));
                String columnName = resultSet.getString(SqlSchemaUtils.COLUMN_NAME_COLUMN);
                if (columnName != null) {
                    index.columns().put(resultSet.getInt("ORDINAL_POSITION"), columnName);
                }
            },
            indexes::clear);
        for (String tableKey : readTables) {
            List<SqlIndexMetadata> indexMetadata = new ArrayList<>();
            indexes.getOrDefault(tableKey, Map.of()).forEach((name, index) ->
                indexMetadata.add(new SqlIndexMetadata(name, index.unique(), new ArrayList<>(index.columns().values()))));
            Objects.requireNonNull(tables.get(tableKey)).setIndexes(indexMetadata);
        }
    }

    /**
     * Reads the table metadata for all the tables with a single call (null table name), which many drivers support.
     * When the driver rejects it, or it returns no rows, the metadata is read with a call per table.
     *
     * @return The lower case names of the tables whose metadata was read
     */
    @SuppressWarnings("java:S107")
    private Set<String> readTablesMetadata(String what,
                                           Map<String, SqlTableMetadata> tables,
                                           String catalogColumn,
                                           String schemaColumn,
                                           String tableNameColumn,
                                           MetadataCall call,
                                           TableRowReader rowReader,
                                           Runnable reset) {
        SqlTableMetadata anyTable = tables.values().iterator().next();
        try (ResultSet resultSet = call.execute(anyTable.getCatalog(), anyTable.getSchema(), null)) {
            boolean rowsRead = false;
            while (resultSet.next()) {
                rowsRead = true;
                String tableName = resultSet.getString(tableNameColumn);
                if (tableName == null) {
                    continue;
                }
                String tableKey = tableName.toLowerCase(Locale.ENGLISH);
                SqlTableMetadata table = tables.get(tableKey);
                if (table != null && isSameSchema(table, resultSet.getString(catalogColumn), resultSet.getString(schemaColumn))) {
                    rowReader.read(tableKey, resultSet);
                }
            }
            if (rowsRead) {
                return tables.keySet();
            }
        } catch (SQLException | RuntimeException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to read {} of all the tables with a single call, reading them per table: {}", what, e.getMessage());
            }
        }
        reset.run();
        Set<String> readTables = new LinkedHashSet<>();
        for (Map.Entry<String, SqlTableMetadata> entry : tables.entrySet()) {
            SqlTableMetadata table = entry.getValue();
            try (ResultSet resultSet = call.execute(table.getCatalog(), table.getSchema(), table.getName())) {
                while (resultSet.next()) {
                    rowReader.read(entry.getKey(), resultSet);
                }
                readTables.add(entry.getKey());
            } catch (SQLException | RuntimeException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unable to read {} of table [{}], skipping their validation: {}", what, table.getName(), e.getMessage(), e);
                }
            }
        }
        return readTables;
    }

    /**
     * A row read for all the tables (null table name) can belong to a same-named table in another catalog or schema
     * matched by the pattern. Some drivers don't report the catalog or schema, only a different reported value is a mismatch.
     */
    private static boolean isSameSchema(SqlTableMetadata table, @Nullable String catalog, @Nullable String schema) {
        return sameOrUnknown(table.getCatalog(), catalog) && sameOrUnknown(table.getSchema(), schema);
    }

    private static boolean sameOrUnknown(@Nullable String expected, @Nullable String actual) {
        return expected == null || actual == null || expected.equalsIgnoreCase(actual);
    }

    private @Nullable String apply(@Nullable String identifier) {
        return identifier == null ? null : namingStrategy.apply(identifier);
    }

    private static IdentifierNamingStrategy getIdentifierNamingStrategy(DatabaseMetaData metaData) throws SQLException {
        if (metaData.storesUpperCaseIdentifiers()) {
            return IdentifierNamingStrategy.UPPER;
        }
        if (metaData.storesLowerCaseIdentifiers()) {
            return IdentifierNamingStrategy.LOWER;
        }
        // default MIXED
        return IdentifierNamingStrategy.MIXED;
    }

    /**
     * The tables read from a schema.
     *
     * @param schema The schema (database for MySQL) as stored in the database, can be null
     * @param tables The table metadata by lower case table name
     */
    record SchemaTables(@Nullable String schema, Map<String, SqlTableMetadata> tables) {
    }

    @FunctionalInterface
    private interface MetadataCall {
        ResultSet execute(@Nullable String catalog, @Nullable String schema, @Nullable String table) throws SQLException;
    }

    @FunctionalInterface
    private interface TableRowReader {
        void read(String tableKey, ResultSet resultSet) throws SQLException;
    }

    @FunctionalInterface
    private interface RowReader {
        void read(ResultSet resultSet) throws SQLException;
    }

    private record IndexColumns(boolean unique, Map<Integer, String> columns) {
    }

    private static final class JsonViewBuilder {
        private final String name;
        private final String rootTable;
        private final boolean allowInsert;
        private final boolean allowUpdate;
        private final boolean allowDelete;
        private final @Nullable String status;
        private final Set<String> tables = new LinkedHashSet<>();
        private final List<SqlJsonViewMetadata.Field> fields = new ArrayList<>();

        private JsonViewBuilder(String name, String rootTable, boolean allowInsert, boolean allowUpdate, boolean allowDelete, @Nullable String status) {
            this.name = name;
            this.rootTable = rootTable;
            this.allowInsert = allowInsert;
            this.allowUpdate = allowUpdate;
            this.allowDelete = allowDelete;
            this.status = status;
        }
    }
}
