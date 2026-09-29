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
import io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator;
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlForeignKeyMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlIdentifierMatcher;
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
 * the tables and their columns are read with a single call. The primary keys, indexes and foreign keys are read with a single
 * dialect query for the schema each (see {@link SqlTableMappingValidator#getPrimaryKeysQuery()},
 * {@link SqlTableMappingValidator#getIndexesQuery()} and {@link SqlTableMappingValidator#getForeignKeysQuery()}).
 * Without such a query, or when it fails, they are read with a call per table: the drivers require the table name, they
 * either reject a null table name or return no rows for it. Entities can also be mapped to views, since the tables are
 * resolved from the columns.
 *
 * @author radovanradic
 * @since 5.3.0
 */
@Internal
final class JdbcSchemaMetadataReader {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcSchemaMetadataReader.class);

    private static final String MATCH_ALL = "%";

    private static final String JSON_DUALITY_VIEWS_QUERY = """
        SELECT VIEW_NAME, STATUS
        FROM ALL_JSON_DUALITY_VIEWS WHERE VIEW_OWNER = ?""";
    private static final String JSON_DUALITY_VIEW_TABLES_QUERY = """
        SELECT VIEW_NAME, TABLE_NUMBER, PARENT_TABLE_NUMBER, TABLE_NAME, RELATIONSHIP, ALLOW_INSERT, ALLOW_UPDATE, ALLOW_DELETE
        FROM ALL_JSON_DUALITY_VIEW_TABS WHERE VIEW_OWNER = ?""";
    private static final String JSON_DUALITY_VIEW_COLUMNS_QUERY = """
        SELECT VIEW_NAME, TABLE_NUMBER, COLUMN_NAME, JSON_KEY_NAME
        FROM ALL_JSON_DUALITY_VIEW_TAB_COLS WHERE VIEW_OWNER = ? AND JSON_KEY_NAME IS NOT NULL""";
    // A composite join has a row per column, the distinct rows are the links
    private static final String JSON_DUALITY_VIEW_LINKS_QUERY = """
        SELECT DISTINCT VIEW_NAME, PARENT_TABLE_NAME, CHILD_TABLE_NAME, KEY_NAME
        FROM ALL_JSON_DUALITY_VIEW_LINKS WHERE VIEW_OWNER = ?""";

    private final Connection connection;
    private final DatabaseMetaData metaData;
    private final SqlIdentifierMatcher identifierMatcher;
    private final String searchStringEscape;
    /**
     * Whether the MySQL database is the catalog, the MySQL Connector/J option {@code databaseTerm=SCHEMA} reports it as the schema.
     */
    private final boolean databaseAsCatalog;
    private final MetadataQueries queries;

    JdbcSchemaMetadataReader(Connection connection, Dialect dialect) throws SQLException {
        this(connection, dialect, MetadataQueries.NONE);
    }

    /**
     * @param connection The connection
     * @param dialect The dialect
     * @param queries The dialect queries reading the metadata of a schema with a single query
     * @throws SQLException If the database metadata cannot be read
     */
    JdbcSchemaMetadataReader(Connection connection, Dialect dialect, MetadataQueries queries) throws SQLException {
        this.queries = queries;
        this.connection = connection;
        this.metaData = connection.getMetaData();
        this.identifierMatcher = SqlIdentifierMatcher.of(dialect, getIdentifierNamingStrategy(metaData), metaData.supportsMixedCaseIdentifiers());
        String escape = metaData.getSearchStringEscape();
        this.searchStringEscape = escape == null ? "" : escape;
        this.databaseAsCatalog = dialect == Dialect.MYSQL && (connection.getCatalog() != null || connection.getSchema() == null);
    }

    /**
     * @return The matcher of the mapped names to the database names
     */
    SqlIdentifierMatcher identifierMatcher() {
        return identifierMatcher;
    }

    /**
     * Resolves the schema name as stored in the database, see {@link SqlIdentifierMatcher#resolve(String, boolean)}.
     *
     * @param schema The schema name as declared in the mapping
     * @param escape Whether the mapping escapes the names
     * @return The schema name as stored in the database, null for the connection default schema
     */
    @Nullable String resolveSchema(@Nullable String schema, boolean escape) {
        return StringUtils.isEmpty(schema) ? null : identifierMatcher.resolve(schema, escape);
    }

    /**
     * Reads the metadata of the given tables in the schema.
     *
     * @param schema The schema name as stored in the database (see {@link #resolveSchema(String, boolean)}), null for the connection default schema
     * @param wantedTableNames The keys of the tables to read (see {@link SqlIdentifierMatcher#mappedTableKey(String, boolean)}), other tables are skipped
     * @param readIndexes Whether to read the indexes, only needed when they are validated
     * @param readForeignKeys Whether to read the foreign keys
     * @return The schema tables
     * @throws SQLException If reading the metadata fails
     */
    SchemaTables readTables(@Nullable String schema, Set<String> wantedTableNames, boolean readIndexes, boolean readForeignKeys) throws SQLException {
        // The connection catalog and schema are the names as stored in the database, a quoted name keeps its case
        String catalog = connection.getCatalog();
        if (schema == null) {
            String currentSchema = databaseAsCatalog ? null : connection.getSchema();
            return readTables(catalog, currentSchema, wantedTableNames, readIndexes, readForeignKeys);
        }
        return databaseAsCatalog
            ? readTables(schema, null, wantedTableNames, readIndexes, readForeignKeys)
            : readTables(catalog, schema, wantedTableNames, readIndexes, readForeignKeys);
    }

    /**
     * Reads the names of the sequences of the schema.
     *
     * @param query The query selecting the sequence names, see {@link io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator#getSequenceNamesQuery()}
     * @param schema The schema (database for MySQL) as stored in the database
     * @return The keys of the sequence names, see {@link SqlIdentifierMatcher#tableKey(String)}
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
                        sequenceNames.add(identifierMatcher.tableKey(name));
                    }
                }
            }
        }
        return sequenceNames;
    }

    /**
     * Reads the full column type definitions of the schema tables with a single query, see
     * {@link io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator#getColumnTypeDefinitionsQuery()}.
     * <p>
     * They are needed to validate the columns mapped with a definition, like {@code VECTOR(3, FLOAT32)}: the metadata
     * ({@link DatabaseMetaData#getColumns(String, String, String, String)}) only reports the type name, not its arguments.
     * Oracle reports {@code VECTOR}, the dimension, format and storage are only in {@code ALL_TAB_COLS.VECTOR_INFO},
     * PostgreSQL reports {@code vector} without the dimension and MySQL the vector size in bytes. A column with a different
     * vector dimension or storage cannot store the mapped vectors.
     * <p>
     * Failures are ignored and the type arguments are not verified.
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
                    SqlTableMetadata table = tableName == null ? null : schemaTables.tables().get(identifierMatcher.tableKey(tableName));
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
            views.put(viewName.toLowerCase(Locale.ENGLISH), new JsonViewBuilder(viewName, resultSet.getString(2)));
        });
        if (views.isEmpty()) {
            return Map.of();
        }
        query(JSON_DUALITY_VIEW_TABLES_QUERY, schema, resultSet -> {
            JsonViewBuilder view = views.get(resultSet.getString(1).toLowerCase(Locale.ENGLISH));
            if (view != null) {
                int tableNumber = resultSet.getInt(2);
                int parentNumber = resultSet.getInt(3);
                // The root table has no parent, wasNull applies to the last read column
                Integer parent = resultSet.wasNull() ? null : parentNumber;
                view.tables.add(new SqlJsonViewMetadata.Table(tableNumber, parent, resultSet.getString(4), resultSet.getString(5),
                    isTrue(resultSet.getString(6)), isTrue(resultSet.getString(7)), isTrue(resultSet.getString(8))));
            }
        });
        query(JSON_DUALITY_VIEW_COLUMNS_QUERY, schema, resultSet -> {
            JsonViewBuilder view = views.get(resultSet.getString(1).toLowerCase(Locale.ENGLISH));
            if (view != null) {
                view.fields.add(new SqlJsonViewMetadata.Field(resultSet.getInt(2), resultSet.getString(4), resultSet.getString(3)));
            }
        });
        query(JSON_DUALITY_VIEW_LINKS_QUERY, schema, resultSet -> {
            JsonViewBuilder view = views.get(resultSet.getString(1).toLowerCase(Locale.ENGLISH));
            if (view != null) {
                view.links.add(new SqlJsonViewMetadata.Link(resultSet.getString(2), resultSet.getString(3), resultSet.getString(4)));
            }
        });
        Map<String, SqlJsonViewMetadata> result = LinkedHashMap.newLinkedHashMap(views.size());
        views.forEach((key, view) -> result.put(key, new SqlJsonViewMetadata(view.name, view.status, view.tables, view.fields, view.links)));
        return result;
    }

    /**
     * The dictionary reports the flags as the strings {@code true} and {@code false}, compared ignoring the case.
     */
    private static boolean isTrue(@Nullable String value) {
        return value != null && StringUtils.isTrue(value.toLowerCase(Locale.ROOT));
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
                                    Set<String> wantedTableNames,
                                    boolean readIndexes,
                                    boolean readForeignKeys) throws SQLException {
        Map<String, SqlTableMetadata> tables = new LinkedHashMap<>();
        String expectedSchema = databaseAsCatalog ? catalog : schema;
        String resolvedSchema = expectedSchema;
        // The tables and their columns are read with a single call, the tables (and views) are the owners of the columns.
        // The schema is a pattern, its wildcard characters are escaped and the rows of the other schemas are skipped.
        try (ResultSet resultSet = metaData.getColumns(catalog, schema == null ? null : escapePattern(schema), null, MATCH_ALL)) {
            while (resultSet.next()) {
                SqlTableMetadata table = columnTable(resultSet, expectedSchema, wantedTableNames, tables);
                if (table != null) {
                    addColumn(table, resultSet);
                    resolvedSchema = databaseAsCatalog ? table.getCatalog() : table.getSchema();
                }
            }
        }
        if (tables.isEmpty()) {
            return new SchemaTables(resolvedSchema, tables);
        }
        readPrimaryKeys(resolvedSchema, tables);
        markViews(catalog, schema, tables);
        if (readIndexes) {
            readIndexes(resolvedSchema, tables);
        }
        if (readForeignKeys) {
            readForeignKeys(resolvedSchema, tables);
        }
        return new SchemaTables(resolvedSchema, tables);
    }

    /**
     * Returns the table owning the column of the current row, created with its first column.
     *
     * @return The table, or null when the column belongs to a table without a mapped entity, or to a table of another schema
     * (the schema pattern can match more schemas)
     */
    private @Nullable SqlTableMetadata columnTable(ResultSet resultSet,
                                                   @Nullable String expectedSchema,
                                                   Set<String> wantedTableNames,
                                                   Map<String, SqlTableMetadata> tables) throws SQLException {
        String tableName = resultSet.getString(SqlSchemaUtils.TABLE_NAME_COLUMN);
        String tableKey = identifierMatcher.tableKey(tableName);
        if (!wantedTableNames.contains(tableKey)) {
            return null;
        }
        String tableCatalog = resultSet.getString(SqlSchemaUtils.TABLE_CATALOG_COLUMN);
        String tableSchema = resultSet.getString(SqlSchemaUtils.TABLE_SCHEMA_COLUMN);
        if (!sameOrUnknown(expectedSchema, databaseAsCatalog ? tableCatalog : tableSchema)) {
            return null;
        }
        SqlTableMetadata table = tables.computeIfAbsent(tableKey,
            key -> new SqlTableMetadata(tableCatalog, tableSchema, tableName, identifierMatcher));
        // Not a table with the same name in another schema
        return Objects.equals(table.getCatalog(), tableCatalog) && Objects.equals(table.getSchema(), tableSchema) ? table : null;
    }

    /**
     * Marks the views, which have no primary key or indexes. The entities can be mapped to views, since the tables
     * are resolved from the columns. Only a table without a primary key can be a view, the views are read
     * with a single call only when there is such a table.
     */
    private void markViews(@Nullable String catalog, @Nullable String schema, Map<String, SqlTableMetadata> tables) {
        boolean withoutPrimaryKey = tables.values().stream()
            .anyMatch(table -> table.getPrimaryKeyColumns() != null && table.getPrimaryKeyColumns().isEmpty());
        if (!withoutPrimaryKey) {
            return;
        }
        try (ResultSet resultSet = metaData.getTables(catalog, schema == null ? null : escapePattern(schema), MATCH_ALL, new String[]{"VIEW"})) {
            while (resultSet.next()) {
                String tableName = resultSet.getString(SqlSchemaUtils.TABLE_NAME_COLUMN);
                SqlTableMetadata table = tableName == null ? null : tables.get(identifierMatcher.tableKey(tableName));
                if (table != null && isSameSchema(table, resultSet.getString(SqlSchemaUtils.TABLE_CATALOG_COLUMN),
                    resultSet.getString(SqlSchemaUtils.TABLE_SCHEMA_COLUMN))) {
                    table.setView(true);
                }
            }
        } catch (SQLException | RuntimeException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to read the views, the tables without a primary key are validated as tables: {}", e.getMessage());
            }
        }
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

    /**
     * Reads the primary keys with the dialect query for the schema, falls back to a call per table without it or when it fails.
     */
    private void readPrimaryKeys(@Nullable String schema, Map<String, SqlTableMetadata> tables) {
        Map<String, Map<Integer, String>> primaryKeys = new HashMap<>();
        Set<String> readTables;
        if (readSchemaQuery("primary keys", queries.primaryKeys(), schema, tables, (tableKey, resultSet) -> {
            String columnName = resultSet.getString(2);
            if (columnName != null) {
                primaryKeys.computeIfAbsent(tableKey, k -> new TreeMap<>()).put(resultSet.getInt(3), columnName);
            }
        })) {
            readTables = tables.keySet();
        } else {
            primaryKeys.clear();
            readTables = readTablesMetadata("primary keys", tables,
                metaData::getPrimaryKeys,
                (tableKey, resultSet) -> primaryKeys.computeIfAbsent(tableKey, k -> new TreeMap<>())
                    .put(resultSet.getInt("KEY_SEQ"), resultSet.getString(SqlSchemaUtils.COLUMN_NAME_COLUMN)));
        }
        // A table without a primary key has none
        for (String tableKey : readTables) {
            Objects.requireNonNull(tables.get(tableKey)).setPrimaryKeyColumns(new ArrayList<>(primaryKeys.getOrDefault(tableKey, Map.of()).values()));
        }
    }

    private void readIndexes(@Nullable String schema, Map<String, SqlTableMetadata> tables) {
        Map<String, Map<String, IndexColumns>> indexes = new HashMap<>();
        Set<String> readTables;
        if (readSchemaQuery("indexes", queries.indexes(), schema, tables, (tableKey, resultSet) ->
            addIndexColumn(indexes, tableKey, resultSet.getString(2), resultSet.getInt(3) != 0, resultSet.getString(4), resultSet.getInt(5)))) {
            readTables = tables.keySet();
        } else {
            indexes.clear();
            // approximate = true, some drivers (Oracle) would otherwise compute the table statistics
            readTables = readTablesMetadata("indexes", tables,
                (catalog, tableSchema, table) -> metaData.getIndexInfo(catalog, tableSchema, table, false, true),
                (tableKey, resultSet) -> {
                    if (resultSet.getShort("TYPE") != DatabaseMetaData.tableIndexStatistic) {
                        // A partial index (with a filter condition) is not unique for all the rows
                        boolean unique = !resultSet.getBoolean("NON_UNIQUE") && StringUtils.isEmpty(filterCondition(resultSet));
                        addIndexColumn(indexes, tableKey, resultSet.getString("INDEX_NAME"), unique,
                            resultSet.getString(SqlSchemaUtils.COLUMN_NAME_COLUMN), resultSet.getInt("ORDINAL_POSITION"));
                    }
                });
        }
        for (String tableKey : readTables) {
            List<SqlIndexMetadata> indexMetadata = new ArrayList<>();
            indexes.getOrDefault(tableKey, Map.of()).forEach((name, index) ->
                indexMetadata.add(new SqlIndexMetadata(name, index.unique(), new ArrayList<>(index.columns().values()))));
            Objects.requireNonNull(tables.get(tableKey)).setIndexes(indexMetadata);
        }
    }

    private void readForeignKeys(@Nullable String schema, Map<String, SqlTableMetadata> tables) {
        Map<String, Map<String, ForeignKeyColumns>> foreignKeys = new HashMap<>();
        Set<String> readTables;
        if (readSchemaQuery("foreign keys", queries.foreignKeys(), schema, tables, (tableKey, resultSet) ->
            addForeignKeyColumn(foreignKeys.computeIfAbsent(tableKey, k -> new LinkedHashMap<>()), resultSet.getString(2),
                resultSet.getString(4), resultSet.getString(5), resultSet.getString(3), resultSet.getString(6), resultSet.getInt(7)))) {
            readTables = tables.keySet();
        } else {
            foreignKeys.clear();
            readTables = readTablesMetadata("foreign keys", tables,
                (catalog, tableSchema, table) -> metaData.getImportedKeys(catalog, tableSchema, table),
                (tableKey, resultSet) -> addForeignKeyColumn(foreignKeys.computeIfAbsent(tableKey, k -> new LinkedHashMap<>()),
                    resultSet.getString("FK_NAME"), resultSet.getString("PKTABLE_SCHEM"), resultSet.getString("PKTABLE_NAME"),
                    resultSet.getString("FKCOLUMN_NAME"), resultSet.getString("PKCOLUMN_NAME"), resultSet.getInt("KEY_SEQ")));
        }
        for (String tableKey : readTables) {
            List<SqlForeignKeyMetadata> foreignKeyMetadata = new ArrayList<>();
            for (ForeignKeyColumns fk : foreignKeys.getOrDefault(tableKey, Map.of()).values()) {
                foreignKeyMetadata.add(new SqlForeignKeyMetadata(fk.name(), new ArrayList<>(fk.columns().values()),
                    fk.referencedSchema(), fk.referencedTable(), new ArrayList<>(fk.referencedColumns().values())));
            }
            Objects.requireNonNull(tables.get(tableKey)).setForeignKeys(foreignKeyMetadata);
        }
    }

    @SuppressWarnings("java:S107")
    private static void addForeignKeyColumn(Map<String, ForeignKeyColumns> tableForeignKeys,
                                            @Nullable String name,
                                            @Nullable String referencedSchema,
                                            String referencedTable,
                                            String column,
                                            String referencedColumn,
                                            int keySeq) {
        String key;
        if (StringUtils.isNotEmpty(name)) {
            key = name;
        } else {
            // Unnamed foreign keys to the same table are separated by the column position, the rows are ordered
            // by the referenced table and the position, so the columns of the foreign keys can be interleaved
            int group = 0;
            ForeignKeyColumns existing = tableForeignKeys.get(unnamedForeignKeyKey(referencedSchema, referencedTable, group));
            while (existing != null && existing.columns().containsKey(keySeq)) {
                group++;
                existing = tableForeignKeys.get(unnamedForeignKeyKey(referencedSchema, referencedTable, group));
            }
            key = unnamedForeignKeyKey(referencedSchema, referencedTable, group);
        }
        ForeignKeyColumns foreignKey = tableForeignKeys.computeIfAbsent(key,
            k -> new ForeignKeyColumns(name, referencedSchema, referencedTable, new TreeMap<>(), new TreeMap<>()));
        foreignKey.columns().put(keySeq, column);
        foreignKey.referencedColumns().put(keySeq, referencedColumn);
    }

    private static String unnamedForeignKeyKey(@Nullable String referencedSchema, String referencedTable, int group) {
        return referencedSchema + "." + referencedTable + "#" + group;
    }

    /**
     * @return The filter condition of a partial index, null when the index has none or the driver doesn't report it
     */
    private static @Nullable String filterCondition(ResultSet resultSet) {
        try {
            return resultSet.getString("FILTER_CONDITION");
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Adds an index column, an index without a column name (an expression) is kept, since it can be matched by its name.
     */
    private static void addIndexColumn(Map<String, Map<String, IndexColumns>> indexes,
                                       String tableKey,
                                       @Nullable String indexName,
                                       boolean unique,
                                       @Nullable String columnName,
                                       int position) {
        if (indexName == null) {
            return;
        }
        IndexColumns index = indexes.computeIfAbsent(tableKey, k -> new LinkedHashMap<>())
            .computeIfAbsent(indexName, name -> new IndexColumns(unique, new TreeMap<>()));
        if (columnName != null) {
            index.columns().put(position, columnName);
        }
    }

    /**
     * Reads the metadata of the schema tables with a single dialect query, the query selects the table name first
     * and has a single parameter, the schema.
     *
     * @return Whether the metadata was read, false without a query or when it fails, the metadata is then read per table
     */
    private boolean readSchemaQuery(String what,
                                    @Nullable String query,
                                    @Nullable String schema,
                                    Map<String, SqlTableMetadata> tables,
                                    TableRowReader rowReader) {
        if (query == null || schema == null) {
            return false;
        }
        try {
            query(query, schema, resultSet -> {
                String tableName = resultSet.getString(1);
                String tableKey = tableName == null ? null : identifierMatcher.tableKey(tableName);
                if (tableKey != null && tables.containsKey(tableKey)) {
                    rowReader.read(tableKey, resultSet);
                }
            });
            return true;
        } catch (SQLException | RuntimeException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to read the {} of schema [{}] with a single query, reading them per table: {}", what, schema, e.getMessage());
            }
            return false;
        }
    }

    /**
     * Reads the table metadata with a call per table. A single call for all the tables (a null table name) is not
     * used: H2, MySQL and MariaDB reject it, SQL Server and Oracle return no rows and PostgreSQL returns no indexes.
     *
     * @return The keys of the tables whose metadata was read
     */
    private Set<String> readTablesMetadata(String what,
                                           Map<String, SqlTableMetadata> tables,
                                           MetadataCall call,
                                           TableRowReader rowReader) {
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
     * A row read for all the tables can belong to a same-named table in another catalog or schema matched by the pattern.
     * Some drivers don't report the catalog or schema, only a different reported value is a mismatch.
     */
    private boolean isSameSchema(SqlTableMetadata table, @Nullable String catalog, @Nullable String schema) {
        return sameOrUnknown(table.getCatalog(), catalog) && sameOrUnknown(table.getSchema(), schema);
    }

    private boolean sameOrUnknown(@Nullable String expected, @Nullable String actual) {
        return expected == null || actual == null || identifierMatcher.tableKey(expected).equals(identifierMatcher.tableKey(actual));
    }

    /**
     * @return The name as a metadata pattern matching only the name
     */
    private String escapePattern(String name) {
        if (searchStringEscape.isEmpty()) {
            return name;
        }
        return name.replace(searchStringEscape, searchStringEscape + searchStringEscape)
            .replace("_", searchStringEscape + "_")
            .replace("%", searchStringEscape + "%");
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
     * @param tables The table metadata by table key, see {@link SqlIdentifierMatcher#tableKey(String)}
     */
    record SchemaTables(@Nullable String schema, Map<String, SqlTableMetadata> tables) {
    }

    /**
     * The dialect queries reading the metadata of all the tables of a schema with a single query, a null query reads the
     * metadata per table.
     *
     * @param primaryKeys The primary keys query, see {@link SqlTableMappingValidator#getPrimaryKeysQuery()}
     * @param indexes The indexes query, see {@link SqlTableMappingValidator#getIndexesQuery()}
     * @param foreignKeys The foreign keys query, see {@link SqlTableMappingValidator#getForeignKeysQuery()}
     */
    record MetadataQueries(@Nullable String primaryKeys, @Nullable String indexes, @Nullable String foreignKeys) {

        static final MetadataQueries NONE = new MetadataQueries(null, null, null);

        static MetadataQueries of(SqlTableMappingValidator validator) {
            return new MetadataQueries(validator.getPrimaryKeysQuery(), validator.getIndexesQuery(), validator.getForeignKeysQuery());
        }
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

    private record ForeignKeyColumns(@Nullable String name,
                                     @Nullable String referencedSchema,
                                     String referencedTable,
                                     Map<Integer, String> columns,
                                     Map<Integer, String> referencedColumns) {
    }

    private static final class JsonViewBuilder {
        private final String name;
        private final @Nullable String status;
        private final List<SqlJsonViewMetadata.Table> tables = new ArrayList<>();
        private final List<SqlJsonViewMetadata.Field> fields = new ArrayList<>();
        private final List<SqlJsonViewMetadata.Link> links = new ArrayList<>();

        private JsonViewBuilder(String name, @Nullable String status) {
            this.name = name;
            this.status = status;
        }
    }
}
