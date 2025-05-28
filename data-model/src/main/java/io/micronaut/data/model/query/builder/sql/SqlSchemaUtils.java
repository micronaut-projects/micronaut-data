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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.exceptions.SchemaValidationException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.SqlDbType;
import io.micronaut.data.model.schema.sql.SqlSequenceMapping;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata;

import java.lang.annotation.Annotation;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.micronaut.data.annotation.GeneratedValue.Type.AUTO;

/**
 * Utility class providing methods for working with SQL schema definitions.
 *
 * @author radovanradic
 * @since 4.13.0
 */
@Internal
public final class SqlSchemaUtils {

    // Table and column metadata columns
    public static final String TABLE_TYPE = "TABLE";
    public static final String TABLE_CATALOG_COLUMN = "TABLE_CAT";
    public static final String TABLE_SCHEMA_COLUMN = "TABLE_SCHEM";
    public static final String TABLE_NAME_COLUMN = "TABLE_NAME";
    public static final String COLUMN_NAME_COLUMN = "COLUMN_NAME";
    public static final String DATA_TYPE_COLUMN = "DATA_TYPE";
    public static final String TYPE_NAME_COLUMN = "TYPE_NAME";
    public static final String COLUMN_SIZE_COLUMN = "COLUMN_SIZE";
    public static final String DECIMAL_DIGITS_COLUMN = "DECIMAL_DIGITS";
    public static final String NULLABLE_COLUMN = "NULLABLE";

    private SqlSchemaUtils() {
    }

    /**
     * Returns list of {@link SqlTableMapping} for persistent entity. It will contain main entity table
     * and potentially joined tables.
     *
     * @param entity The entity
     * @return The SQL table definitions for the given entity
     * @since 4.13.0
     */
    @Experimental
    @NonNull
    @SuppressWarnings("java:S3776")
    public static List<SqlTableMapping> getSqlTableMappings(@NonNull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);

        final String tableName = entity.getPersistedName();
        String schema = SqlQueryBuilderUtils.getSchemaName(entity);

        List<SqlTableMapping> tables = new ArrayList<>();

        Collection<Association> foreignKeyAssociations = SqlQueryBuilderUtils.getJoinTableAssociations(entity);

        NamingStrategy namingStrategy = entity.getNamingStrategy();
        if (CollectionUtils.isNotEmpty(foreignKeyAssociations)) {
            for (Association association : foreignKeyAssociations) {
                PersistentEntity associatedEntity = association.getAssociatedEntity();
                List<SqlColumnMapping> columns = new ArrayList<>();

                Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
                Association owningAssociation = inverseSide.orElse(association);
                AnnotationMetadata annotationMetadata = owningAssociation.getAnnotationMetadata();

                String joinTableName = annotationMetadata
                    .stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, "name")
                    .orElseGet(() ->
                        namingStrategy.mappedName(association)
                    );
                String joinTableSchema = annotationMetadata.stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, SqlMembers.SCHEMA).orElse(null);
                if (!StringUtils.isNotEmpty(joinTableSchema)) {
                    joinTableSchema = schema;
                }
                List<PersistentPropertyPath> leftProperties = new ArrayList<>();
                List<PersistentPropertyPath> rightProperties = new ArrayList<>();
                boolean isAssociationOwner = inverseSide.isEmpty();
                List<String> leftJoinTableColumns = SqlQueryBuilderUtils.resolveJoinTableJoinColumns(annotationMetadata,
                    isAssociationOwner, entity, namingStrategy);
                List<String> rightJoinTableColumns = SqlQueryBuilderUtils.resolveJoinTableJoinColumns(annotationMetadata,
                    !isAssociationOwner, association.getAssociatedEntity(), namingStrategy);
                PersistentProperty property2 = entity.getIdentity();
                PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), property2, (associations1, property3)
                    -> leftProperties.add(PersistentPropertyPath.of(associations1, property3, "")));
                PersistentProperty property1 = associatedEntity.getIdentity();
                PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), property1, (associations, property)
                    -> rightProperties.add(PersistentPropertyPath.of(associations, property, "")));
                if (leftJoinTableColumns.size() == leftProperties.size()) {
                    for (int i = 0; i < leftJoinTableColumns.size(); i++) {
                        PersistentPropertyPath pp = leftProperties.get(i);
                        String columnName = leftJoinTableColumns.get(i);
                        // TODO: Should we treat join table fields as primary keys?
                        columns.add(getColumnDefinition(pp.getProperty(), columnName, false, true, true));
                    }
                } else {
                    for (PersistentPropertyPath pp : leftProperties) {
                        String columnName = namingStrategy.mappedJoinTableColumn(entity, pp.getAssociations(), pp.getProperty());
                        columns.add(getColumnDefinition(pp.getProperty(), columnName, false, true, true));
                    }
                }
                if (rightJoinTableColumns.size() == rightProperties.size()) {
                    for (int i = 0; i < rightJoinTableColumns.size(); i++) {
                        PersistentPropertyPath pp = rightProperties.get(i);
                        String columnName = rightJoinTableColumns.get(i);
                        columns.add(getColumnDefinition(pp.getProperty(), columnName, false, true, true));
                    }
                } else {
                    for (PersistentPropertyPath pp : rightProperties) {
                        String columnName = namingStrategy.mappedJoinTableColumn(entity, pp.getAssociations(), pp.getProperty());
                        columns.add(getColumnDefinition(pp.getProperty(), columnName, false, true, true));
                    }
                }
                SqlTableMapping joinTable = new SqlTableMapping(joinTableSchema, joinTableName, SqlTableMapping.TableType.JOIN, null, columns);
                tables.add(joinTable);
            }
        }

        List<SqlColumnMapping> primaryKeyColumns = new ArrayList<>();
        List<SqlColumnMapping> columns = new ArrayList<>();

        List<PersistentProperty> identities = entity.getIdentityProperties();
        for (PersistentProperty identity : identities) {
            List<PersistentPropertyPath> ids = new ArrayList<>();
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property)
                -> ids.add(PersistentPropertyPath.of(associations, property, "")));
            for (PersistentPropertyPath pp : ids) {
                String columnName = namingStrategy.mappedName(pp.getAssociations(), pp.getProperty());
                SqlColumnMapping column = getColumnDefinition(pp.getProperty(), columnName, true,
                    isRequired(pp.getAssociations(), pp.getProperty()), !SqlQueryBuilderUtils.isNotForeign(pp.getAssociations()));
                primaryKeyColumns.add(column);
            }
        }

        PersistentProperty version = entity.getVersion();
        if (version != null && !version.isGenerated()) {
            String columnName = namingStrategy.mappedName(Collections.emptyList(), version);
            SqlColumnMapping column = getColumnDefinition(version, columnName, false, true, false);
            columns.add(column);
        }

        BiConsumer<List<Association>, PersistentProperty> addColumn = (associations, property) -> {
            String columnName = namingStrategy.mappedName(associations, property);
            SqlColumnMapping column = getColumnDefinition(property, columnName, false, isRequired(associations, property),
                !SqlQueryBuilderUtils.isNotForeign(associations));
            columns.add(column);
        };

        for (PersistentProperty prop : entity.getPersistentProperties()) {
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), prop, addColumn);
        }

        List<SqlSequenceMapping> sequences = new ArrayList<>();
        for (PersistentProperty identity : identities) {
            if (identity.isGenerated()) {
                GeneratedValue.Type idGeneratorType = identity.getAnnotationMetadata()
                    .enumValue(GeneratedValue.class, GeneratedValue.Type.class)
                    .orElse(null);
                final String generatedDefinition = identity.getAnnotationMetadata().stringValue(GeneratedValue.class, "definition").orElse(null);
                sequences.add(new SqlSequenceMapping(generatedDefinition, identity.getDataType(), Optional.ofNullable(idGeneratorType)));
            }
        }

        SqlTableMapping table = new SqlTableMapping(schema, tableName, SqlTableMapping.TableType.MAIN, primaryKeyColumns, columns, sequences);
        tables.add(table);
        return tables;
    }

    /**
     * Validates a table definition based on {@link PersistentEntity} mapping against its actual corresponding metadata from the database.
     *
     * @param tableMapping    the SQL table mapping from {@link PersistentEntity} to validate
     * @param tableMetadata   the SQL table metadata from the database to compare against
     * @param dialect         the SQL dialect of the schema
     * @throws SchemaValidationException When expected column not found or is not matching expected type
     */
    public static void validateTable(@NonNull SqlTableMapping tableMapping, @NonNull SqlTableMetadata tableMetadata, @NonNull Dialect dialect) {
        List<SqlColumnMapping> primaryKeyColumns = tableMapping.primaryKeyColumns();
        List<SqlColumnMapping> columns = tableMapping.columns();
        List<SqlColumnMapping> allColumns = new ArrayList<>(columns.size() + (primaryKeyColumns != null ? primaryKeyColumns.size() : 0));
        if (primaryKeyColumns != null) {
            allColumns.addAll(primaryKeyColumns);
        }
        allColumns.addAll(columns);
        for (SqlColumnMapping columnMapping : allColumns) {
            String name = columnMapping.getName();
            SqlColumnMetadata columnMetadata = tableMetadata.getColumn(name.toLowerCase());
            if (columnMetadata == null) {
                throw new SchemaValidationException("Schema validation failed. Column [" + name + "] not found in the table [" + tableMapping.name() + "]");
            }
            validateColumn(columnMapping, columnMetadata, dialect, tableMetadata.getName());
        }
    }

    /**
     * Validates a single column mapping definition against its corresponding metadata from the database.
     *
     * @param columnMapping     the SQL column mapping from {@link PersistentEntity} field to validate
     * @param columnMetadata    the SQL column metadata from the database to compare against
     * @param dialect           the dialect of the schema where column belongs
     * @param tableName         the name of the table where column is stored
     * @throws SchemaValidationException when the expected column does not match the actual column metadata
     */
    private static void validateColumn(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata,
                                       Dialect dialect, String tableName) {
        if (StringUtils.isNotEmpty(columnMapping.getDefinition())) {
            // Don't compare columns with custom SQL definition
            // and let user be responsible for mapping of that field
            return;
        }
        if (matchingColumn(columnMapping, columnMetadata, dialect)) {
            return;
        }
        throw new SchemaValidationException(String.format("Schema validation failed. Column [%s] in table [%s] of type [%s] is mapped to [%s].",
            columnMetadata.name(), tableName, columnMetadata.typeName(), columnMapping.getDbType()));
    }

    private static boolean matchingColumn(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata, Dialect dialect) {
        if (matchingColumnTypes(columnMapping.getDbType(), columnMetadata.type())) {
            return true;
        }
        String sqlType = columnMapping.getSqlType(dialect);
        if (sqlType.equalsIgnoreCase(columnMetadata.typeName())) {
            return true;
        }
        if (dialect == Dialect.ORACLE) {
            return matchOracleColumn(columnMapping, columnMetadata);
        } else if (dialect == Dialect.MYSQL) {
            return matchMySqlColumn(columnMapping, columnMetadata);
        } else if (dialect == Dialect.H2) {
            return matchH2Column(columnMapping, columnMetadata);
        } else if (dialect == Dialect.POSTGRES) {
            return matchPostgresColumn(columnMapping, columnMetadata);
        } else if (dialect == Dialect.SQL_SERVER) {
            return matchSqlServerColumn(columnMapping, columnMetadata);
        }
        // Add other rules for matching if needed
        return false;
    }

    private static boolean matchOracleColumn(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        if (columnMapping.getDbType() == SqlDbType.UUID) {
            return uuidMatchesVarchar(columnMetadata);
        } else if (columnMetadata.type() == Types.NUMERIC) {
            // Custom sql type name for ORACLE
            String oracleSqlType = "NUMBER";
            if (columnMetadata.columnSize() > 0) {
                oracleSqlType += "(" + columnMetadata.columnSize();
                if (columnMetadata.decimalDigits() > 0) {
                    oracleSqlType += "," + columnMetadata.decimalDigits();
                }
                oracleSqlType += ")";
            }
            return columnMapping.getSqlType(Dialect.ORACLE).equalsIgnoreCase(oracleSqlType);
        } else if (isOracleBinaryDoubleOrFloat(columnMetadata.typeName())) {
            return isFloatOrRealOrDouble(columnMapping.getDbType().getType());
        }
        return false;
    }

    private static boolean isOracleBinaryDoubleOrFloat(String typeName) {
        return "BINARY_DOUBLE".equalsIgnoreCase(typeName) || "BINARY_FLOAT".equalsIgnoreCase(typeName);
    }

    private static boolean matchMySqlColumn(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        if (columnMapping.getDbType() == SqlDbType.UUID) {
            return uuidMatchesVarchar(columnMetadata) ||
                // For MariaDB
                (columnMetadata.type() == Types.OTHER && columnMetadata.typeName().equalsIgnoreCase("uuid"));
        }
        if (columnMapping.getDbType() == SqlDbType.BOOLEAN) {
            return columnMetadata.type() == Types.BIT;
        }
        if (columnMapping.getDbType() == SqlDbType.JSON) {
            return columnMetadata.type() == Types.LONGVARCHAR;
        }
        return false;
    }

    private static boolean matchH2Column(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        if (columnMapping.getDbType() == SqlDbType.BINARY) {
            return columnMetadata.type() == Types.BLOB;
        }
        return false;
    }

    private static boolean matchPostgresColumn(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        if (columnMapping.getDbType() == SqlDbType.BOOLEAN) {
            return columnMetadata.type() == Types.BIT;
        }
        return false;
    }

    private static boolean matchSqlServerColumn(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        if (columnMapping.getDbType() == SqlDbType.BINARY) {
            return columnMetadata.type() == Types.VARBINARY;
        }
        if (columnMapping.getDbType() == SqlDbType.JSON) {
            return columnMetadata.type() == Types.NVARCHAR;
        }
        return false;
    }

    private static boolean uuidMatchesVarchar(SqlColumnMetadata columnMetadata) {
        return columnMetadata.type() == Types.VARCHAR && columnMetadata.columnSize() == 36;
    }

    private static boolean matchingColumnTypes(SqlDbType dbType, int typeCode) {
        int mappedTypeCode = dbType.getType();
        return mappedTypeCode == typeCode
                || isCompatibleIntegralType(mappedTypeCode, typeCode)
                || isNumericOrDecimal(mappedTypeCode) && isNumericOrDecimal(typeCode)
                || isFloatOrRealOrDouble(mappedTypeCode) && isFloatOrRealOrDouble(typeCode)
                || isVarcharType(mappedTypeCode) && isVarcharType(typeCode)
                || isVarbinaryType(mappedTypeCode) && isVarbinaryType(typeCode)
                || isEnumType(mappedTypeCode) && isVarcharType(typeCode);
        // Add more checks/fallbacks during testing and/or reported issues
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

    private static boolean isFloatOrRealOrDouble(int typeCode) {
        return switch (typeCode) {
            case Types.FLOAT, Types.REAL, Types.DOUBLE -> true;
            default -> false;
        };
    }

    private static boolean isVarcharType(int typeCode) {
        return switch (typeCode) {
            case Types.VARCHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> true;
            default -> false;
        };
    }

    private static boolean isVarbinaryType(int typeCode) {
        return switch (typeCode) {
            case Types.VARBINARY, Types.LONGVARBINARY -> true;
            default -> false;
        };
    }

    private static boolean isEnumType(int typeCode) {
        return typeCode == SqlDbType.ENUM.getType();
    }

    /**
     * Creates a new Column object based on the provided PersistentProperty and other mapped field attributes.
     *
     * @param prop         the PersistentProperty to create the Column for
     * @param column       the name of the column
     * @param primaryKey   whether the column is a primary key
     * @param required     whether the column is required
     * @param isForeign    whether the column is a foreign key
     * @return             a new Column object representing the provided PersistentProperty
     * @throws IllegalStateException if the provided property is an Association
     * @throws MappingException      if the data type of the property is unknown
     */
    private static SqlColumnMapping getColumnDefinition(PersistentProperty prop, String column, boolean primaryKey, boolean required,
                                                        boolean isForeign) {
        if (prop instanceof Association) {
            throw new IllegalStateException("Association is not supported here");
        }
        AnnotationMetadata annotationMetadata = prop.getAnnotationMetadata();
        String definition = annotationMetadata.stringValue(MappedProperty.class, "definition").orElse(null);
        DataType dataType = prop.getDataType();
        boolean autoGenerated = !isForeign && prop.isGenerated();
        GeneratedValue.Type generatedValueType = autoGenerated ? prop.getAnnotationMetadata().enumValue(GeneratedValue.class, GeneratedValue.Type.class)
            .orElse(AUTO) : null;
        OptionalInt optPrecision = SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "precision");
        OptionalInt optScale = SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "scale");

        SqlDbType dbType = getDbType(prop);

        Integer precision = null;
        Integer scale = null;

        return switch (dataType) {
            case STRING -> {
                int stringLength = annotationMetadata.findAnnotation("jakarta.validation.constraints.Size$List")
                    .flatMap(v -> {
                        Optional value = v.getValue(AnnotationValue.class);
                        return (Optional<AnnotationValue<Annotation>>) value;
                    }).map(v -> v.intValue("max"))
                    .orElseGet(() -> SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "length"))
                    .orElse(255);

                yield new SqlColumnMapping(column, dataType, dbType, primaryKey, stringLength, required, autoGenerated, generatedValueType, definition);
            }
            case UUID, BOOLEAN, TIMESTAMP, DATE, TIME, LONG, SHORT, BYTE,
                BYTE_ARRAY, STRING_ARRAY, CHARACTER_ARRAY, SHORT_ARRAY, INTEGER_ARRAY,
                LONG_ARRAY, FLOAT_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY -> new SqlColumnMapping(column, dataType, dbType, primaryKey,
                null, required, autoGenerated, generatedValueType, definition);
            case CHARACTER -> new SqlColumnMapping(column, dataType, dbType, primaryKey, 1, required, autoGenerated, generatedValueType, definition);
            case JSON -> new SqlColumnMapping(column, dataType, dbType, primaryKey, null, precision, scale, required, autoGenerated, generatedValueType,
                definition, prop.getJsonDataType());
            case INTEGER -> {
                if (optPrecision.isPresent()) {
                    // TODO: Does precision make sense for integer
                    precision = optPrecision.getAsInt();
                }
                yield new SqlColumnMapping(column, dataType, dbType, primaryKey, null, precision, required, autoGenerated, generatedValueType,
                    definition);
            }
            case BIGDECIMAL, FLOAT, DOUBLE -> {
                // TODO: Should only BigDecimal support precision and scale (like Hibernate?)
                if (optPrecision.isPresent()) {
                    precision = optPrecision.getAsInt();
                }
                if (optScale.isPresent()) {
                    scale = optScale.getAsInt();
                }
                yield new SqlColumnMapping(column, dataType, dbType, primaryKey, null, precision, scale, required, autoGenerated, generatedValueType,
                    definition, null);
            }
            default -> {
                if (StringUtils.isNotEmpty(definition)) {
                    yield new SqlColumnMapping(column, dataType, dbType, primaryKey, null, required, autoGenerated, generatedValueType, definition);
                }
                throw new MappingException("Unable to create table column for property [" + prop.getName() + "] of entity [" + prop.getOwner().getName() + "] with unknown data type: " + dataType);
            }
        };
    }

    /**
     * Returns the database type corresponding to the given persistent property.
     *
     * @param property the persistent property
     * @return the database type
     * @throws IllegalStateException if the property is an association
     * @throws MappingException if the data type of the property is unknown
     */
    private static SqlDbType getDbType(PersistentProperty property) {
        DataType dataType = property.getDataType();

        return switch (dataType) {
            case STRING -> SqlDbType.VARCHAR;
            case UUID -> SqlDbType.UUID;
            case BOOLEAN -> SqlDbType.BOOLEAN;
            case TIMESTAMP -> SqlDbType.TIMESTAMP;
            case DATE -> SqlDbType.DATE;
            case TIME -> SqlDbType.TIME;
            case LONG -> SqlDbType.BIGINT;
            case CHARACTER -> SqlDbType.CHAR;
            case INTEGER -> SqlDbType.INTEGER;
            case BIGDECIMAL -> SqlDbType.NUMERIC;
            case FLOAT -> SqlDbType.FLOAT;
            case BYTE_ARRAY -> SqlDbType.BINARY;
            case DOUBLE -> SqlDbType.DOUBLE;
            case SHORT, BYTE -> SqlDbType.SMALLINT;
            case JSON -> SqlDbType.JSON;
            case STRING_ARRAY, CHARACTER_ARRAY, SHORT_ARRAY, INTEGER_ARRAY,
                LONG_ARRAY, FLOAT_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY -> SqlDbType.ARRAY;
            default -> {
                if (property.isEnum()) {
                    yield SqlDbType.ENUM;
                } else if (property.isAssignable(Clob.class)) {
                    yield SqlDbType.CLOB;
                } else if (property.isAssignable(Blob.class)) {
                    yield SqlDbType.BLOB;
                } else {
                    throw new MappingException("Unable to create table column for property [" + property.getName() + "] of entity [" + property.getOwner().getName() + "] with unknown data type: " + dataType);
                }
            }
        };
    }

    /**
     * Determines whether a property is required based on its associations and own requirements.
     *
     * This method checks the associations of the given property and returns false if any of them are not required.
     * If there are no associations or all associations are required, it then checks the requirement status of the property itself.
     * If a foreign association exists, its requirement status takes precedence over the property's own requirement status.
     *
     * @param associations the associations of the property
     * @param property the property to check
     * @return true if the property is required, false otherwise
     */
    private static boolean isRequired(List<Association> associations, PersistentProperty property) {
        Association foreignAssociation = null;
        for (Association association : associations) {
            if (!association.isRequired()) {
                return false;
            }
            if (association.getKind() != Relation.Kind.EMBEDDED) {
                if (foreignAssociation == null) {
                    foreignAssociation = association;
                }
            }
        }
        if (foreignAssociation != null) {
            return foreignAssociation.isRequired();
        }
        return property.isRequired();
    }
}
