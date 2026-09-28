/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.model.query.builder.sql.validation;

import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions;
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils;
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.SqlDbType;
import io.micronaut.data.model.schema.sql.SqlIndexMapping;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlIdentifierMatcher;
import io.micronaut.data.model.schema.sql.metadata.SqlIndexMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata;
import org.jspecify.annotations.Nullable;

import java.sql.Types;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An abstract base class for validating SQL table mappings against actual table metadata from the database.
 * <p>
 * This class provides a basic implementation of the {@link SqlTableMappingValidator} interface,
 * including validation of table and column definitions extracted from a {@link PersistentEntity}
 * against the corresponding metadata from the database.
 * <p>
 * Missing columns and incompatible column types are reported as errors. Nullability, length and precision differences,
 * and missing primary keys and indexes are reported as warnings.
 * <p>
 * Subclasses are expected to implement the {@link #getSupportedDialect()} method to specify the SQL dialect
 * supported by the validator and optionally override {@link #matchingDialectColumnType(SqlColumnMapping, SqlColumnMetadata, SqlDialectOptions)}
 * to provide dialect-specific column type matching logic.
 *
 * @since 4.13.0
 * @author radovanradic
 */
abstract class BaseSqlTableMappingValidator implements SqlTableMappingValidator {

    /**
     * Types defined through a column definition (by a definition provider or a spatial type) that are verified strictly.
     * Other custom definitions are verified on the best effort basis and a difference is only reported as a warning.
     */
    private static final Set<String> STRICT_DEFINITION_TYPES = Set.of(
        "VECTOR", "SPARSEVEC", "HALFVEC", "GEOMETRY", "GEOGRAPHY", "SDO_GEOMETRY"
    );

    private static final Set<String> VECTOR_TYPES = Set.of("VECTOR", "SPARSEVEC", "HALFVEC");

    private static final String DENSE_VECTOR_STORAGE = "DENSE";

    /**
     * The column size reported for character and binary columns without a length limit (H2 reports its maximal length).
     */
    private static final int UNBOUNDED_COLUMN_SIZE = 1_000_000_000;

    /**
     * Keywords that can follow the type name in a column definition.
     */
    private static final Set<String> TYPE_MODIFIER_KEYWORDS = Set.of(
        "NOT", "NULL", "DEFAULT", "PRIMARY", "UNIQUE", "CHECK", "REFERENCES", "GENERATED", "AUTO_INCREMENT",
        "IDENTITY", "CONSTRAINT", "COLLATE", "RESERVABLE", "UNSIGNED", "SIGNED", "ZEROFILL"
    );

    /**
     * Synonyms of the type names used by the supported databases.
     */
    private static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
        Map.entry("INT", "INTEGER"),
        Map.entry("INT4", "INTEGER"),
        Map.entry("SERIAL", "INTEGER"),
        Map.entry("SERIAL4", "INTEGER"),
        Map.entry("INT8", "BIGINT"),
        Map.entry("BIGSERIAL", "BIGINT"),
        Map.entry("SERIAL8", "BIGINT"),
        Map.entry("INT2", "SMALLINT"),
        Map.entry("SMALLSERIAL", "SMALLINT"),
        Map.entry("FLOAT8", "DOUBLE"),
        Map.entry("DOUBLE PRECISION", "DOUBLE"),
        Map.entry("FLOAT4", "REAL"),
        Map.entry("BOOL", "BOOLEAN"),
        Map.entry("VARCHAR2", "VARCHAR"),
        Map.entry("CHARACTER VARYING", "VARCHAR"),
        Map.entry("NVARCHAR2", "NVARCHAR"),
        Map.entry("NATIONAL CHARACTER VARYING", "NVARCHAR"),
        Map.entry("CHARACTER", "CHAR"),
        Map.entry("BPCHAR", "CHAR"),
        Map.entry("DECIMAL", "NUMERIC"),
        Map.entry("DEC", "NUMERIC"),
        Map.entry("NUMBER", "NUMERIC"),
        Map.entry("TIMESTAMP WITH TIME ZONE", "TIMESTAMPTZ"),
        Map.entry("TIMESTAMP WITHOUT TIME ZONE", "TIMESTAMP"),
        Map.entry("TIME WITH TIME ZONE", "TIMETZ"),
        Map.entry("TIME WITHOUT TIME ZONE", "TIME"),
        Map.entry("BINARY LARGE OBJECT", "BLOB"),
        Map.entry("CHARACTER LARGE OBJECT", "CLOB")
    );

    private static final Pattern TYPE_ARGUMENTS = Pattern.compile("\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\)");
    private static final Pattern PARENTHESES = Pattern.compile("\\([^()]*\\)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public final void validateTable(SqlTableMapping tableMapping,
                                    SqlTableMetadata tableMetadata,
                                    SqlDialectOptions dialectOptions) {
        SchemaValidationResult result = new SchemaValidationResult();
        validateTable(tableMapping, tableMetadata, dialectOptions, result);
        result.throwIfErrors();
    }

    @Override
    public final void validateTable(SqlTableMapping tableMapping,
                                    SqlTableMetadata tableMetadata,
                                    SqlDialectOptions dialectOptions,
                                    SchemaValidationResult result) {
        List<SqlColumnMapping> primaryKeyColumns = tableMapping.primaryKeyColumns() == null ? List.of() : tableMapping.primaryKeyColumns();
        for (SqlColumnMapping columnMapping : primaryKeyColumns) {
            validateColumn(tableMapping, columnMapping, tableMetadata, dialectOptions, true, result);
        }
        for (SqlColumnMapping columnMapping : tableMapping.columns()) {
            validateColumn(tableMapping, columnMapping, tableMetadata, dialectOptions, false, result);
        }
        if (tableMetadata.isView()) {
            // A view has no primary key or indexes
            return;
        }
        validatePrimaryKey(tableMapping, tableMetadata, dialectOptions, result);
        validateIndexes(tableMapping, tableMetadata, dialectOptions, result);
    }

    /**
     * Checks if the column type defined in the {@link SqlColumnMapping} matches the actual column type
     * retrieved from the database metadata ({@link SqlColumnMetadata}) for the given SQL dialect.
     *
     * @param columnMapping  the SQL column mapping from {@link PersistentEntity} field
     * @param columnMetadata the SQL column metadata from the database
     * @param dialect        the SQL dialect to consider during type comparison
     * @return true if the column type matches, false otherwise
     */
    protected final boolean matchingColumnType(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata,
                                               Dialect dialect) {
        return matchingColumnType(columnMapping, columnMetadata, SqlDialectOptions.defaults(dialect));
    }

    /**
     * Checks if the column type defined in the {@link SqlColumnMapping} matches the actual column type
     * retrieved from the database metadata ({@link SqlColumnMetadata}) for the given SQL dialect.
     * <p>
     * The method performs a multi-step comparison:
     * <ol>
     *     <li>Compares the type code of {@link SqlColumnMapping#getDbType()} with the type code from {@link SqlColumnMetadata#type()},
     *         accepting compatible types of the same family (for example widening integral types or character types).</li>
     *     <li>Compares the SQL type generated by {@link SqlColumnMapping#getSqlType(SqlDialectOptions)} with the type name
     *         from {@link SqlColumnMetadata#typeName()}, ignoring type arguments and synonyms (see {@link #normalizeTypeName(String)}).</li>
     *     <li>Delegates to {@link #matchingDialectColumnType(SqlColumnMapping, SqlColumnMetadata, SqlDialectOptions)}
     *         for dialect-specific matching logic.</li>
     * </ol>
     * Length and precision are not part of the type comparison, see {@link #validateTypeArguments}.
     *
     * @param columnMapping  the SQL column mapping from {@link PersistentEntity} field
     * @param columnMetadata the SQL column metadata from the database
     * @param dialectOptions the dialect options
     * @return true if the column type matches, false otherwise
     */
    protected final boolean matchingColumnType(SqlColumnMapping columnMapping,
                                               SqlColumnMetadata columnMetadata,
                                               SqlDialectOptions dialectOptions) {
        if (matchingColumnTypes(columnMapping.getDbType(), columnMetadata)) {
            return true;
        }
        String sqlType = columnMapping.getSqlType(dialectOptions);
        if (sqlType.equalsIgnoreCase(columnMetadata.typeName())) {
            return true;
        }
        String expectedType = normalizeTypeName(sqlType);
        if (!expectedType.isEmpty() && expectedType.equals(normalizeTypeName(columnMetadata.typeName()))) {
            return true;
        }
        return matchingDialectColumnType(columnMapping, columnMetadata, dialectOptions);
    }

    /**
     * Provides dialect-specific logic for matching the column type defined in the {@link SqlColumnMapping}
     * with the actual column type retrieved from the database metadata ({@link SqlColumnMetadata}).
     * <p>
     * The default implementation always returns false, indicating that the column types are not matching. Subclasses
     * can override this method to provide more specific type matching logic for their supported dialect.
     *
     * @param columnMapping  the SQL column mapping from {@link PersistentEntity} field
     * @param columnMetadata the SQL column metadata from the database
     * @return true if the column type matches according to the dialect-specific logic, false otherwise
     */
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        return false;
    }

    /**
     * Provides dialect-specific logic for matching the column type defined in the {@link SqlColumnMapping}
     * with the actual column type retrieved from the database metadata ({@link SqlColumnMetadata}).
     *
     * @param columnMapping  the SQL column mapping from {@link PersistentEntity} field
     * @param columnMetadata the SQL column metadata from the database
     * @param dialectOptions the dialect options
     * @return true if the column type matches according to the dialect-specific logic, false otherwise
     */
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping,
                                                SqlColumnMetadata columnMetadata,
                                                SqlDialectOptions dialectOptions) {
        return matchingDialectColumnType(columnMapping, columnMetadata);
    }

    /**
     * Provides dialect-specific logic for matching the type of column declared with an explicit definition
     * (for example {@code @MappedProperty(definition = "...")}, a vector or a spatial type) with the actual column type.
     *
     * @param expectedType   the normalized type name of the definition, see {@link #normalizeTypeName(String)}
     * @param columnMetadata the SQL column metadata from the database
     * @param dialectOptions the dialect options
     * @return true if the column type matches according to the dialect-specific logic, false otherwise
     */
    protected boolean matchingDialectDefinedColumnType(String expectedType,
                                                       SqlColumnMetadata columnMetadata,
                                                       SqlDialectOptions dialectOptions) {
        return false;
    }

    /**
     * Normalizes the type name, so that type names reported by the database can be compared with the declared types.
     * The type arguments (length, precision, scale), quotes, schema prefix and the modifiers following the type are removed,
     * and the synonyms are replaced with a single name (for example {@code int4}, {@code INT} and {@code INTEGER}
     * are all normalized to {@code INTEGER}).
     *
     * @param typeName the type name or column definition
     * @return the normalized type name
     */
    protected static String normalizeTypeName(@Nullable String typeName) {
        if (typeName == null) {
            return "";
        }
        String type = typeName.toUpperCase(Locale.ENGLISH)
            .replace("\"", "")
            .replace("`", "")
            .replace("[", "")
            .replace("]", "");
        String previous;
        do {
            previous = type;
            type = PARENTHESES.matcher(type).replaceAll(" ");
        } while (!type.equals(previous));
        StringBuilder normalized = new StringBuilder();
        for (String token : WHITESPACE.splitAsStream(type.trim()).filter(t -> !t.isEmpty()).toList()) {
            if (!normalized.isEmpty() && TYPE_MODIFIER_KEYWORDS.contains(token)) {
                break;
            }
            if (!normalized.isEmpty()) {
                normalized.append(' ');
            }
            normalized.append(token);
        }
        String name = normalized.toString();
        int schemaSeparator = name.lastIndexOf('.');
        if (schemaSeparator >= 0 && name.indexOf(' ') < 0) {
            name = name.substring(schemaSeparator + 1);
        }
        return TYPE_ALIASES.getOrDefault(name, name);
    }

    /**
     * Checks if the given JDBC type code represents a floating-point data type.
     *
     * @param typeCode the JDBC type code to check
     * @return true if the type code is FLOAT, REAL, or DOUBLE, false otherwise
     */
    protected static boolean isFloatOrRealOrDouble(int typeCode) {
        return switch (typeCode) {
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> true;
            default -> false;
        };
    }

    /**
     * Checks if the given JDBC type code represents a character data type.
     *
     * @param typeCode the JDBC type code to check
     * @return true if the type code is a character (including character large object) type
     */
    protected static boolean isCharacterType(int typeCode) {
        return switch (typeCode) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
                 Types.CLOB, Types.NCLOB -> true;
            default -> false;
        };
    }

    private void validateColumn(SqlTableMapping tableMapping,
                                SqlColumnMapping columnMapping,
                                SqlTableMetadata tableMetadata,
                                SqlDialectOptions dialectOptions,
                                boolean primaryKey,
                                SchemaValidationResult result) {
        String name = columnMapping.getName();
        SqlColumnMetadata columnMetadata = tableMetadata.getMappedColumn(name, tableMapping.escape());
        if (columnMetadata == null) {
            result.addError("Column [" + name + "] not found in the table [" + tableMapping.name() + "]");
            return;
        }
        String tableName = tableMetadata.getName();
        String definition = columnMapping.getDefinition();
        if (StringUtils.isNotEmpty(definition)) {
            validateDefinedColumnType(definition, columnMetadata, tableMetadata.getColumnTypeDefinition(columnMetadata.name()),
                dialectOptions, tableName, result);
            // Nullability is part of the definition, which is the responsibility of the user
            return;
        }
        if (!matchingColumnType(columnMapping, columnMetadata, dialectOptions)) {
            result.addError(String.format("Column [%s] in table [%s] of type [%s] is mapped to [%s]",
                columnMetadata.name(), tableName, columnMetadata.typeName(), columnMapping.getDbType()));
            return;
        }
        validateTypeArguments(columnMapping, columnMetadata, dialectOptions, tableName, result);
        if (!primaryKey && !columnMapping.isAutoGenerated()) {
            validateNullability(columnMapping, columnMetadata, tableName, result);
        }
    }

    private void validateDefinedColumnType(String definition,
                                           SqlColumnMetadata columnMetadata,
                                           @Nullable String actualDefinition,
                                           SqlDialectOptions dialectOptions,
                                           String tableName,
                                           SchemaValidationResult result) {
        String expectedType = normalizeTypeName(definition);
        String actualType = normalizeTypeName(columnMetadata.typeName());
        if (expectedType.isEmpty()) {
            return;
        }
        if (expectedType.equals(actualType)
            || (actualDefinition != null && expectedType.equals(normalizeTypeName(actualDefinition)))
            || matchingDialectDefinedColumnType(expectedType, columnMetadata, dialectOptions)) {
            if (VECTOR_TYPES.contains(expectedType) && actualDefinition != null) {
                validateVectorArguments(definition, actualDefinition, columnMetadata, tableName, result);
            }
            return;
        }
        String message = String.format("Column [%s] in table [%s] of type [%s] is mapped to definition [%s]",
            columnMetadata.name(), tableName, columnMetadata.typeName(), definition);
        if (STRICT_DEFINITION_TYPES.contains(expectedType) || STRICT_DEFINITION_TYPES.contains(actualType)) {
            result.addError(message);
        } else {
            // A custom definition can use any of the database type synonyms, don't fail on a difference that could be a false positive
            result.addWarning(message + " (custom column definitions are not verified strictly)");
        }
    }

    /**
     * Compares the vector dimension, element format and storage, for example {@code VECTOR(3,FLOAT32)} with
     * {@code VECTOR(3,FLOAT32,DENSE)} or {@code vector(3)} with {@code vector(3)}. A flexible ({@code *}) argument matches any value.
     * <p>
     * A different dimension or storage (dense or sparse) is an error, since such vectors cannot be stored in the column.
     * A different element format is a warning, since the database converts the elements.
     */
    private static void validateVectorArguments(String definition,
                                                String actualDefinition,
                                                SqlColumnMetadata columnMetadata,
                                                String tableName,
                                                SchemaValidationResult result) {
        List<String> expected = typeArguments(definition);
        List<String> actual = typeArguments(actualDefinition);
        if (expected.isEmpty() || actual.isEmpty()) {
            return;
        }
        String expectedDimension = expected.getFirst();
        String actualDimension = actual.getFirst();
        if (differentVectorArgument(expectedDimension, actualDimension)) {
            result.addError(String.format("Column [%s] in table [%s] has vector dimension [%s] but the mapped dimension is [%s]",
                columnMetadata.name(), tableName, actualDimension, expectedDimension));
        }
        if (expected.size() > 1 && actual.size() > 1) {
            String expectedFormat = expected.get(1);
            String actualFormat = actual.get(1);
            if (differentVectorArgument(expectedFormat, actualFormat)) {
                result.addWarning(String.format("Column [%s] in table [%s] has vector format [%s] but the mapped format is [%s]",
                    columnMetadata.name(), tableName, actualFormat, expectedFormat));
            }
        }
        if (actual.size() > 2) {
            // The storage is reported by Oracle, a definition without the storage declares a dense vector
            String expectedStorage = expected.size() > 2 ? expected.get(2) : DENSE_VECTOR_STORAGE;
            String actualStorage = actual.get(2);
            if (differentVectorArgument(expectedStorage, actualStorage)) {
                result.addError(String.format("Column [%s] in table [%s] has vector storage [%s] but the mapped storage is [%s]",
                    columnMetadata.name(), tableName, actualStorage, expectedStorage));
            }
        }
    }

    private static boolean differentVectorArgument(String expected, String actual) {
        return !isFlexible(expected) && !isFlexible(actual) && !expected.equalsIgnoreCase(actual);
    }

    private static boolean isFlexible(String argument) {
        return "*".equals(argument);
    }

    /**
     * @return the arguments of the first parenthesized type arguments list, e.g. [3, FLOAT32] for {@code VECTOR(3, FLOAT32) NOT NULL}
     */
    private static List<String> typeArguments(String definition) {
        int start = definition.indexOf('(');
        int end = start < 0 ? -1 : definition.indexOf(')', start);
        if (end < 0) {
            return List.of();
        }
        return Arrays.stream(definition.substring(start + 1, end).split(","))
            .map(String::trim)
            .filter(argument -> !argument.isEmpty())
            .toList();
    }

    /**
     * Reports columns that are shorter or less precise than the mapped column type.
     */
    private void validateTypeArguments(SqlColumnMapping columnMapping,
                                       SqlColumnMetadata columnMetadata,
                                       SqlDialectOptions dialectOptions,
                                       String tableName,
                                       SchemaValidationResult result) {
        if (columnMetadata.columnSize() <= 0) {
            return;
        }
        String sqlType = columnMapping.getSqlType(dialectOptions);
        String expectedType = normalizeTypeName(sqlType);
        Matcher matcher = TYPE_ARGUMENTS.matcher(sqlType);
        if (!matcher.find()) {
            return;
        }
        int expectedSize = Integer.parseInt(matcher.group(1));
        switch (expectedType) {
            case "VARCHAR", "NVARCHAR", "CHAR" -> {
                if (isCharacterType(columnMetadata.type()) && columnMetadata.columnSize() < expectedSize) {
                    String message = String.format("Column [%s] in table [%s] has length [%d] which is less than the mapped length [%d]",
                        columnMetadata.name(), tableName, columnMetadata.columnSize(), expectedSize);
                    if (columnMapping.getDataType() == DataType.UUID) {
                        // A UUID stored as a string always has the full length, no value can be stored
                        result.addError(message);
                    } else {
                        // Only the values longer than the column length cannot be stored
                        result.addWarning(message);
                    }
                }
            }
            case "NUMERIC" -> {
                if (columnMetadata.type() != Types.NUMERIC && columnMetadata.type() != Types.DECIMAL) {
                    return;
                }
                int expectedScale = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
                if (columnMetadata.columnSize() < expectedSize || columnMetadata.decimalDigits() != expectedScale) {
                    result.addWarning(String.format("Column [%s] in table [%s] has precision and scale [%d,%d] which is different from the mapped [%d,%d]",
                        columnMetadata.name(), tableName, columnMetadata.columnSize(), columnMetadata.decimalDigits(), expectedSize, expectedScale));
                }
            }
            default -> {
                // Other type arguments (fractional seconds, float binary precision) are not checked
            }
        }
    }

    private void validateNullability(SqlColumnMapping columnMapping,
                                     SqlColumnMetadata columnMetadata,
                                     String tableName,
                                     SchemaValidationResult result) {
        if (columnMapping.isRequired() && columnMetadata.nullable()) {
            result.addWarning(String.format("Column [%s] in table [%s] is nullable but the mapped property is required",
                columnMetadata.name(), tableName));
        } else if (!columnMapping.isRequired() && !columnMetadata.nullable()) {
            result.addWarning(String.format("Column [%s] in table [%s] is NOT NULL but the mapped property is nullable, storing null values will fail",
                columnMetadata.name(), tableName));
        }
    }

    private void validatePrimaryKey(SqlTableMapping tableMapping,
                                    SqlTableMetadata tableMetadata,
                                    SqlDialectOptions dialectOptions,
                                    SchemaValidationResult result) {
        List<String> actualPrimaryKey = tableMetadata.getPrimaryKeyColumns();
        List<SqlColumnMapping> primaryKeyColumns = tableMapping.primaryKeyColumns();
        if (actualPrimaryKey == null || primaryKeyColumns == null || primaryKeyColumns.isEmpty()) {
            return;
        }
        List<String> expectedPrimaryKey = primaryKeyColumns.stream().map(SqlColumnMapping::getName).toList();
        if (actualPrimaryKey.isEmpty()) {
            if (dialectOptions.dialect() == Dialect.MYSQL && primaryKeyColumns.size() == 1
                && !primaryKeyColumns.getFirst().isAutoGenerated() && primaryKeyColumns.getFirst().getDataType() == DataType.BYTE_ARRAY) {
                // The schema generation doesn't create the MySQL primary key of a byte array (BLOB) identity, it requires a key length
                return;
            }
            result.addWarning(String.format("Table [%s] has no primary key, the mapped primary key is %s", tableMapping.name(), expectedPrimaryKey));
        } else if (!storedColumnKeys(tableMetadata, actualPrimaryKey).equals(mappedColumnKeys(tableMapping, tableMetadata, expectedPrimaryKey))) {
            result.addWarning(String.format("Table [%s] has primary key %s which is different from the mapped primary key %s",
                tableMapping.name(), actualPrimaryKey, expectedPrimaryKey));
        }
    }

    private void validateIndexes(SqlTableMapping tableMapping,
                                 SqlTableMetadata tableMetadata,
                                 SqlDialectOptions dialectOptions,
                                 SchemaValidationResult result) {
        List<SqlIndexMetadata> indexes = tableMetadata.getIndexes();
        if (indexes == null) {
            return;
        }
        for (SqlIndexMapping indexMapping : tableMapping.indexes()) {
            SqlIdentifierMatcher matcher = tableMetadata.getIdentifierMatcher();
            List<String> columns = Arrays.stream(indexMapping.columns()).map(column -> matcher.mappedColumnKey(column, tableMapping.escape())).toList();
            boolean special = indexMapping.spatial() || indexMapping.sqlIndexDefinitionProvider() != null;
            String expectedName = SqlSchemaUtils.resolveIndexName(tableMapping.name(), indexMapping);
            boolean found = indexes.stream().anyMatch(index -> {
                List<String> indexColumns = index.columns().stream().map(matcher::columnKey).toList();
                if (special) {
                    // The index method (spatial, vector) is not reported by the metadata and an ordinary index on the same column
                    // must not match, spatial and vector indexes are matched by the name, and by the columns when they are reported
                    return matchingIndexName(expectedName, index.name(), matcher, tableMapping.escape(), dialectOptions.dialect())
                        && (indexColumns.isEmpty() || indexColumns.containsAll(columns));
                }
                return indexColumns.equals(columns) && (!indexMapping.unique() || index.unique());
            });
            if (!found) {
                String kind;
                String indexName;
                if (special) {
                    kind = indexMapping.spatial() ? "Spatial index" : "Vector index";
                    indexName = "[" + expectedName + "] ";
                } else {
                    kind = indexMapping.unique() ? "Unique index" : "Index";
                    indexName = StringUtils.isNotEmpty(indexMapping.name()) ? "[" + indexMapping.name() + "] " : "";
                }
                result.addWarning(String.format("%s %son columns %s not found in table [%s]",
                    kind, indexName, Arrays.toString(indexMapping.columns()), tableMapping.name()));
            }
        }
    }

    /**
     * @return whether the index name matches the expected name. The index is created with the escaping of its table,
     * and its name is compared like a column name (case-sensitive where the quoted names are, not for MySQL and SQL Server).
     * PostgreSQL silently truncates the longer identifiers.
     */
    private static boolean matchingIndexName(String expectedName,
                                             @Nullable String indexName,
                                             SqlIdentifierMatcher matcher,
                                             boolean escape,
                                             Dialect dialect) {
        if (indexName == null) {
            return false;
        }
        String resolvedName = matcher.resolve(expectedName, escape);
        if (dialect == Dialect.POSTGRES) {
            resolvedName = SqlIdentifierMatcher.truncatePostgresIdentifier(resolvedName);
        }
        return matcher.columnKey(resolvedName).equals(matcher.columnKey(indexName));
    }

    private static Set<String> storedColumnKeys(SqlTableMetadata tableMetadata, List<String> columns) {
        SqlIdentifierMatcher matcher = tableMetadata.getIdentifierMatcher();
        return columns.stream().map(matcher::columnKey).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> mappedColumnKeys(SqlTableMapping tableMapping, SqlTableMetadata tableMetadata, List<String> columns) {
        SqlIdentifierMatcher matcher = tableMetadata.getIdentifierMatcher();
        return columns.stream().map(column -> matcher.mappedColumnKey(column, tableMapping.escape()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean matchingColumnTypes(SqlDbType dbType, SqlColumnMetadata columnMetadata) {
        int mappedTypeCode = dbType.getType();
        int typeCode = columnMetadata.type();
        if (mappedTypeCode == typeCode) {
            return true;
        }
        return switch (dbType) {
            // A LOB can only be stored in a LOB or unbounded column, a bounded column can reject or truncate the values
            case CLOB, NCLOB -> isCharacterLobType(typeCode) || (isCharacterType(typeCode) && isUnbounded(columnMetadata));
            case BLOB -> isBinaryLobType(typeCode) || (isBinaryType(typeCode) && isUnbounded(columnMetadata));
            default -> isCompatibleIntegralType(mappedTypeCode, typeCode)
                || (isNumericOrDecimal(mappedTypeCode) && isNumericOrDecimal(typeCode))
                || (isFloatOrRealOrDouble(mappedTypeCode) && isFloatOrRealOrDouble(typeCode))
                || (isMappedToCharacterType(dbType) && isCharacterType(typeCode))
                || (isBinaryType(mappedTypeCode) && isBinaryType(typeCode))
                || (isBooleanOrBit(mappedTypeCode) && isBooleanOrBit(typeCode));
        };
    }

    private static boolean isMappedToCharacterType(SqlDbType dbType) {
        return switch (dbType) {
            case CHAR, VARCHAR, LONGVARCHAR, NCHAR, NVARCHAR, LONGNVARCHAR, ENUM -> true;
            default -> false;
        };
    }

    private static boolean isCharacterLobType(int typeCode) {
        return switch (typeCode) {
            case Types.CLOB, Types.NCLOB, Types.LONGVARCHAR, Types.LONGNVARCHAR -> true;
            default -> false;
        };
    }

    private static boolean isBinaryLobType(int typeCode) {
        return typeCode == Types.BLOB || typeCode == Types.LONGVARBINARY;
    }

    /**
     * @return whether the column has no length limit, for example PostgreSQL {@code text}/{@code bytea}, SQL Server {@code VARCHAR(MAX)}
     * or H2 {@code CHARACTER VARYING} without a length (reported with the maximal length of 1_000_000_000)
     */
    private static boolean isUnbounded(SqlColumnMetadata columnMetadata) {
        return columnMetadata.columnSize() <= 0 || columnMetadata.columnSize() >= UNBOUNDED_COLUMN_SIZE;
    }

    private static boolean isCompatibleIntegralType(int typeCode1, int typeCode2) {
        return switch (typeCode1) {
            case Types.TINYINT ->
                typeCode2 == Types.TINYINT
                    || typeCode2 == Types.SMALLINT
                    || typeCode2 == Types.INTEGER
                    || typeCode2 == Types.BIGINT;
            case Types.SMALLINT ->
                typeCode2 == Types.SMALLINT
                    || typeCode2 == Types.INTEGER
                    || typeCode2 == Types.BIGINT;
            case Types.INTEGER ->
                typeCode2 == Types.INTEGER
                    || typeCode2 == Types.BIGINT;
            default -> false;
        };
    }

    private static boolean isNumericOrDecimal(int typeCode) {
        return switch (typeCode) {
            case Types.NUMERIC, Types.DECIMAL ->  true;
            default -> false;
        };
    }

    private static boolean isBinaryType(int typeCode) {
        return switch (typeCode) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> true;
            default -> false;
        };
    }

    private static boolean isBooleanOrBit(int typeCode) {
        return typeCode == Types.BOOLEAN || typeCode == Types.BIT;
    }
}
