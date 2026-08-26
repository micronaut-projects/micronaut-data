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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.VectorIndexType;
import io.micronaut.data.model.runtime.convert.DatabaseType;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.VectorIndex;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Reservable;
import io.micronaut.data.annotation.Srid;
import io.micronaut.data.annotation.VectorShape;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.geo.Geometry;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.runtime.convert.DefinitionProvider;
import io.micronaut.data.model.runtime.convert.SqlColumnDefinitionProvider;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.convert.SqlIndexDefinitionProvider;
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.SqlColumnMapping.ReservableOptions;
import io.micronaut.data.model.schema.sql.SqlColumnMapping.SqlCheckConstraint;
import io.micronaut.data.model.schema.sql.SqlDbType;
import io.micronaut.data.model.schema.sql.SqlIndexMapping;
import io.micronaut.data.model.schema.sql.SqlSequenceMapping;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.model.schema.sql.metadata.VectorIndexMetadata;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Blob;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.core.annotation.AnnotationMetadata.VALUE_MEMBER;
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

    static final String LIST_ANNOTATION_SUFFIX = "$List";

    static final int SRID_WGS_84 = 4326;
    static final int SRID_ETRS_89 = 4258;
    static final int SRID_WEB_MERCATOR = 3857;

    private static final Logger LOG = LoggerFactory.getLogger(SqlSchemaUtils.class);

    private static final int MAX_CONSTRAINT_NAME_LENGTH = 128;
    private static final int CONSTRAINT_NAME_HASH_LENGTH = 12;

    private static final String JAKARTA_SIZE = "jakarta.validation.constraints.Size";
    private static final String JAKARTA_POSITIVE = "jakarta.validation.constraints.Positive";
    private static final String JAVAX_POSITIVE = "javax.validation.constraints.Positive";
    private static final String JAKARTA_POSITIVE_OR_ZERO = "jakarta.validation.constraints.PositiveOrZero";
    private static final String JAVAX_POSITIVE_OR_ZERO = "javax.validation.constraints.PositiveOrZero";
    private static final String JAKARTA_NEGATIVE = "jakarta.validation.constraints.Negative";
    private static final String JAVAX_NEGATIVE = "javax.validation.constraints.Negative";
    private static final String JAKARTA_NEGATIVE_OR_ZERO = "jakarta.validation.constraints.NegativeOrZero";
    private static final String JAVAX_NEGATIVE_OR_ZERO = "javax.validation.constraints.NegativeOrZero";
    private static final String JAKARTA_MIN = "jakarta.validation.constraints.Min";
    private static final String JAVAX_MIN = "javax.validation.constraints.Min";
    private static final String JAKARTA_MAX = "jakarta.validation.constraints.Max";
    private static final String JAVAX_MAX = "javax.validation.constraints.Max";
    private static final String JAKARTA_DECIMAL_MIN = "jakarta.validation.constraints.DecimalMin";
    private static final String JAVAX_DECIMAL_MIN = "javax.validation.constraints.DecimalMin";
    private static final String JAKARTA_DECIMAL_MAX = "jakarta.validation.constraints.DecimalMax";
    private static final String JAVAX_DECIMAL_MAX = "javax.validation.constraints.DecimalMax";

    private static final String ORACLE_GEOM_METADATA_STATEMENT = """
        INSERT INTO USER_SDO_GEOM_METADATA (TABLE_NAME, COLUMN_NAME, DIMINFO, SRID)
        VALUES (
          '%s',
          '%s',
          MDSYS.SDO_DIM_ARRAY(
            MDSYS.SDO_DIM_ELEMENT('X', %s, %s, %s),
            MDSYS.SDO_DIM_ELEMENT('Y', %s, %s, %s)
          ),
          %s
        )""";

    private SqlSchemaUtils() {
    }

    /**
     * Returns SQL table mappings for an entity using no external definition providers.
     *
     * <p>This convenience overload is intended for callers that don't need custom
     * column/index DDL provider extensions.</p>
     *
     * @param entity The entity
     * @param dialect The SQL dialect used to render vendor-specific definitions
     * @return The SQL table definitions for the given entity
     * @since 5.0.0
     */
    @Experimental
    public static List<SqlTableMapping> getSqlTableMappings(PersistentEntity entity,
                                                            Dialect dialect) {
        return getSqlTableMappings(List.of(), entity, dialect);
    }

    /**
     * Returns list of {@link SqlTableMapping} for persistent entity. It will contain main entity table
     * and potentially joined tables.
     *
     * @param definitionProviders the list of DefinitionProvider (column/index DDL providers)
     * @param entity The entity
     * @param dialect The SQL dialect used to render vendor-specific definitions.
     * @return The SQL table definitions for the given entity
     * @since 5.0.0
     */
    @Experimental
    @SuppressWarnings("java:S3776")
    public static List<SqlTableMapping> getSqlTableMappings(List<DefinitionProvider> definitionProviders,
                                                            PersistentEntity entity,
                                                            Dialect dialect) {
        ArgumentUtils.requireNonNull("entity", entity);

        final String tableName = entity.getPersistedName();
        String schema = SqlQueryBuilderUtils.getSchemaName(entity);
        boolean escape = entity.getAnnotationMetadata().booleanValue(MappedEntity.class, "escape").orElse(true);

        List<SqlTableMapping> tables = new ArrayList<>();

        Collection<Association> foreignKeyAssociations = SqlQueryBuilderUtils.getJoinTableAssociations(entity);

        List<SqlColumnDefinitionProvider> sqlColumnDefinitionProviders = definitionProviders.stream().filter(SqlColumnDefinitionProvider.class::isInstance).map(x -> (SqlColumnDefinitionProvider) x).toList();
        List<SqlIndexDefinitionProvider> sqlIndexDefinitionProviders = definitionProviders.stream().filter(SqlIndexDefinitionProvider.class::isInstance).map(x -> (SqlIndexDefinitionProvider) x).toList();

        NamingStrategy namingStrategy = entity.getNamingStrategy();
        if (CollectionUtils.isNotEmpty(foreignKeyAssociations)) {
            for (Association association : foreignKeyAssociations) {
                PersistentEntity associatedEntity = association.getAssociatedEntity();

                Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
                Association owningAssociation = inverseSide.orElse(association);
                AnnotationMetadata annotationMetadata = owningAssociation.getAnnotationMetadata();

                String joinTableName = annotationMetadata
                    .stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, "name")
                    .orElseGet(() ->
                        namingStrategy.mappedName(association));
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
                PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), entity.getIdentity(), (associations1, property3)
                    -> leftProperties.add(PersistentPropertyPath.of(associations1, property3, "")));
                PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), associatedEntity.getIdentity(), (associations, property)
                    -> rightProperties.add(PersistentPropertyPath.of(associations, property, "")));
                List<SqlColumnMapping> joinColumns = new ArrayList<>();
                addJoinTableColumns(sqlColumnDefinitionProviders, entity, namingStrategy, leftProperties, leftJoinTableColumns, dialect, joinColumns);
                addJoinTableColumns(sqlColumnDefinitionProviders, entity, namingStrategy, rightProperties, rightJoinTableColumns, dialect, joinColumns);
                SqlTableMapping joinTable = new SqlTableMapping(joinTableSchema, joinTableName, escape, SqlTableMapping.TableType.JOIN, joinColumns, Collections.emptyList());
                tables.add(joinTable);
            }
        }

        List<PersistentProperty> identities = entity.getIdentityProperties();
        List<SqlColumnMapping> primaryKeyColumns = getPrimaryKeyColumns(sqlColumnDefinitionProviders, identities, namingStrategy, tableName, dialect);

        List<SqlColumnMapping> columns = new ArrayList<>();
        Map<String, String[]> columnPaths = new LinkedHashMap<>();
        for (PersistentProperty identity : identities) {
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property) -> {
                String columnName = namingStrategy.mappedName(associations, property);
                String[] path = SqlQueryBuilderUtils.asPath(associations, property);
                String @Nullable [] existingPath = columnPaths.putIfAbsent(columnName, path);
                if (existingPath != null && !Arrays.equals(existingPath, path)) {
                    failOnConflictingIdentityColumn(entity, columnName, existingPath, path);
                }
            });
        }

        if (entity.hasVersion()) {
            PersistentProperty version = entity.getVersion();
            if (!version.isGenerated()) {
                String columnName = namingStrategy.mappedName(Collections.emptyList(), version);
                SqlColumnMapping column = getColumnDefinition(sqlColumnDefinitionProviders, version, columnName, tableName, false, true, false, dialect);
                addTableColumn(entity, columns, columnPaths, columnName, new String[]{version.getName()}, column);
            }
        }

        BiConsumer<List<Association>, PersistentProperty> addColumn = (associations, property) -> {
            String columnName = namingStrategy.mappedName(associations, property);
            if (SqlQueryBuilderUtils.isSharedIdentityColumn(entity, namingStrategy, associations, property, columnName)) {
                return;
            }
            SqlColumnMapping column = getColumnDefinition(sqlColumnDefinitionProviders, property, columnName, tableName, false, isRequired(associations, property),
                !SqlQueryBuilderUtils.isNotForeign(associations), dialect);
            addTableColumn(entity, columns, columnPaths, columnName, SqlQueryBuilderUtils.asPath(associations, property), column);
        };

        for (PersistentProperty prop : entity.getPersistentProperties()) {
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), prop, addColumn);
        }

        List<SqlSequenceMapping> sequences = getSqlSequenceMappings(identities);
        List<String> auxiliaryStatements = getAuxiliaryStatements(entity, tableName, namingStrategy, dialect);
        List<SqlIndexMapping> indexes = getSqlIndexMappings(entity, dialect, sqlIndexDefinitionProviders);
        validateReservableColumns(entity, primaryKeyColumns, columns, indexes);

        SqlTableMapping table = new SqlTableMapping(schema, tableName, escape, SqlTableMapping.TableType.MAIN, primaryKeyColumns, columns, sequences,
            indexes, auxiliaryStatements);
        tables.add(table);
        return tables;
    }

    private static void failOnConflictingIdentityColumn(PersistentEntity entity, String columnName, String[] existingPath, String[] path) {
        throw new MappingException("Conflicting identity mapping for column [" + columnName + "] on entity [" + entity.getName() + "] between paths "
            + Arrays.toString(existingPath) + " and " + Arrays.toString(path));
    }

    /**
     * Adds a DDL column while detecting multiple property paths mapped to the same physical column.
     *
     * <p>Shared identity relation columns are omitted before this method is called. Other duplicate mappings would
     * generate invalid or ambiguous DDL, so they fail fast instead of silently dropping one property path.</p>
     */
    private static void addTableColumn(PersistentEntity entity,
                                       List<SqlColumnMapping> columns,
                                       Map<String, String[]> columnPaths,
                                       String columnName,
                                       String[] path,
                                       SqlColumnMapping column) {
        String @Nullable [] existingPath = columnPaths.putIfAbsent(columnName, path);
        if (existingPath != null) {
            if (Arrays.equals(existingPath, path)) {
                return;
            }
            throw new MappingException("Conflicting table mapping for column [" + columnName + "] on entity [" + entity.getName() + "] between paths "
                + Arrays.toString(existingPath) + " and " + Arrays.toString(path));
        }
        columns.add(column);
    }

    /**
     * Create Join table columns.
     *
     * @param entity The entity
     * @param namingStrategy The naming strategy
     * @param joinProperties The properties that are used for joining (typically left or right identity)
     * @param joinColumns The corresponding columns that are used for joining
     * @param joinTableColumns The resulting columns used for joing table
     * @param dialect The dialect
     */
    private static void addJoinTableColumns(List<SqlColumnDefinitionProvider> sqlColumnDefinitionProviders,
                                            PersistentEntity entity,
                                            NamingStrategy namingStrategy,
                                            List<PersistentPropertyPath> joinProperties,
                                            List<String> joinColumns,
                                            Dialect dialect,
                                            List<SqlColumnMapping> joinTableColumns) {
        if (joinColumns.size() == joinProperties.size()) {
            for (int i = 0; i < joinColumns.size(); i++) {
                PersistentPropertyPath pp = joinProperties.get(i);
                String columnName = joinColumns.get(i);
                joinTableColumns.add(getColumnDefinition(sqlColumnDefinitionProviders, pp.getProperty(), columnName, entity.getPersistedName(), true, true, true, dialect));
            }
        } else {
            for (PersistentPropertyPath pp : joinProperties) {
                String columnName = namingStrategy.mappedJoinTableColumn(entity, pp.getAssociations(), pp.getProperty());
                joinTableColumns.add(getColumnDefinition(sqlColumnDefinitionProviders, pp.getProperty(), columnName, entity.getPersistedName(), true, true, true, dialect));
            }
        }
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
    @SuppressWarnings({"java:S3776", "java:S107"})
    private static SqlColumnMapping getColumnDefinition(List<SqlColumnDefinitionProvider> columnDefinitionProviders,
                                                        PersistentProperty prop,
                                                        String column,
                                                        String tableName,
                                                        boolean primaryKey,
                                                        boolean required,
                                                        boolean isForeign,
                                                        Dialect dialect) {
        if (prop instanceof Association) {
            throw new IllegalStateException("Association is not supported here");
        }
        AnnotationMetadata annotationMetadata = prop.getAnnotationMetadata();
        String definition = getDefinition(prop, dialect, required);
        boolean reservable = annotationMetadata.hasAnnotation(Reservable.class);
        if (reservable) {
            validateReservableProperty(prop, primaryKey, isForeign, dialect, definition);
        }

        // Resolve Argument for the property (prefer runtime implementation to preserve annotation metadata)
        io.micronaut.core.type.Argument<?> argument;
        if (prop instanceof io.micronaut.data.model.runtime.RuntimePersistentProperty<?> rpp) {
            argument = rpp.getArgument();
        } else {
            // Fallback: best-effort without annotation metadata
            argument = io.micronaut.core.type.Argument.of(Object.class);
        }
        DataType dataType = prop.getDataType();

        // If OBJECT type and no explicit definition yet, consult injected providers
        if (definition == null && dataType == DataType.OBJECT && columnDefinitionProviders != null && !columnDefinitionProviders.isEmpty()) {
            for (SqlColumnDefinitionProvider provider : columnDefinitionProviders) {
                try {
                    if (provider.supports(argument)) {
                        String def = provider.getColumnDefinition(argument, DatabaseType.from(dialect));
                        if (def != null) {
                            definition = required ? def + " NOT NULL" : def;
                            break;
                        }
                    }
                } catch (Exception e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Ignoring SqlColumnDefinitionProvider [{}] failure while resolving definition for property [{}]",
                            provider.getClass().getName(), prop.getName(), e);
                    }
                }
            }
        }
        boolean autoGenerated = !isForeign && prop.isGenerated();
        GeneratedValue.Type generatedValueType = autoGenerated ? prop.getAnnotationMetadata().enumValue(GeneratedValue.class, GeneratedValue.Type.class)
            .orElse(AUTO) : AUTO;
        OptionalInt optPrecision = SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "precision");
        OptionalInt optScale = SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "scale");

        if (annotationMetadata.hasAnnotation(JsonAnyGetter.class) || annotationMetadata.hasAnnotation(JsonAnySetter.class)) {
            return new SqlColumnMapping(column, dataType, SqlDbType.JSON_OBJECT, primaryKey, null, required, autoGenerated, generatedValueType, definition);
        }

        SqlDbType dbType = getDbType(prop, definition);

        Integer precision = null;
        Integer scale = null;

        return switch (dataType) {
            case STRING -> {
                int stringLength = annotationMetadata.findAnnotation(JAKARTA_SIZE + LIST_ANNOTATION_SUFFIX)
                    .flatMap(v -> {
                        Optional value = v.getValue(AnnotationValue.class);
                        return (Optional<AnnotationValue<Annotation>>) value;
                    }).map(v -> v.intValue("max"))
                    .orElseGet(() -> SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "length"))
                    .orElse(255);

                yield columnMapping(column, tableName, dataType, dbType, new ColumnOptions(stringLength, null, null, null, reservable, prop), required, autoGenerated, generatedValueType, definition);
            }
            case UUID, BOOLEAN, TIMESTAMP, DATE, TIME, LONG, SHORT, BYTE,
                BYTE_ARRAY, STRING_ARRAY, CHARACTER_ARRAY, SHORT_ARRAY, INTEGER_ARRAY,
                LONG_ARRAY, FLOAT_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY, UUID_ARRAY ->
                columnMapping(column, tableName, dataType, dbType, new ColumnOptions(null, null, null, null, reservable, prop), required, autoGenerated, generatedValueType, definition);
            case CHARACTER -> columnMapping(column, tableName, dataType, dbType, new ColumnOptions(1, null, null, null, reservable, prop), required, autoGenerated, generatedValueType, definition);
            case JSON -> new SqlColumnMapping(column, dataType, dbType, null, null, null, required, autoGenerated, generatedValueType,
                definition, prop.getJsonDataType());
            case INTEGER -> {
                if (optPrecision.isPresent()) {
                    precision = optPrecision.getAsInt();
                }
                yield columnMapping(column, tableName, dataType, dbType, new ColumnOptions(null, precision, null, null, reservable, prop), required, autoGenerated, generatedValueType, definition);
            }
            case BIGDECIMAL, FLOAT, DOUBLE -> {
                if (optPrecision.isPresent()) {
                    precision = optPrecision.getAsInt();
                }
                if (optScale.isPresent()) {
                    scale = optScale.getAsInt();
                }
                yield columnMapping(column, tableName, dataType, dbType, new ColumnOptions(null, precision, scale, null, reservable, prop), required, autoGenerated, generatedValueType, definition);
            }
            case DURATION, PERIOD -> new SqlColumnMapping(column, dataType, dbType, null, null, null, required, autoGenerated, generatedValueType,
                definition, null);
            default -> {
                if (StringUtils.isNotEmpty(definition)) {
                    yield columnMapping(column, tableName, dataType, dbType, new ColumnOptions(null, null, null, null, reservable, prop), required, autoGenerated, generatedValueType, definition);
                }
                throw new MappingException("Unable to create table column for property [" + prop.getName() + "] of entity [" + prop.getOwner().getName() + "] with unknown data type: " + dataType);
            }
        };
    }

    @Nullable
    private static String getDefinition(PersistentProperty prop, Dialect dialect, boolean required) {
        AnnotationMetadata annotationMetadata = prop.getAnnotationMetadata();
        Optional<String> definitionOpt = annotationMetadata.stringValue(MappedProperty.class, "definition");
        if (definitionOpt.isPresent()) {
            return definitionOpt.get();
        }
        if (prop.isAssignable(Geometry.class)) {
            String definition = null;
            if (dialect == Dialect.ORACLE) {
                definition = "SDO_GEOMETRY";
            } else if ((dialect == Dialect.POSTGRES || dialect == Dialect.SQL_SERVER)
                && SqlQueryBuilderUtils.isGeography(annotationMetadata)) {
                definition = "GEOGRAPHY";
            } else if (dialect == Dialect.MYSQL
                || dialect == Dialect.POSTGRES
                || dialect == Dialect.H2
                || dialect == Dialect.SQL_SERVER) {
                definition = "GEOMETRY";
            }
            if (definition != null) {
                if (required) {
                    definition += " NOT NULL";
                }
                return definition;
            }
        }
        return null;
    }

    @SuppressWarnings("java:S107")
    private static SqlColumnMapping columnMapping(String column,
                                                  String tableName,
                                                  DataType dataType,
                                                  SqlDbType dbType,
                                                  ColumnOptions columnOptions,
                                                  boolean required,
                                                  boolean autoGenerated,
                                                  GeneratedValue.Type generatedValueType,
                                                  @Nullable String definition) {
        List<SqlCheckConstraint> checkConstraints = columnOptions.reservable ? deriveNumericChecks(columnOptions.property, column, tableName) : List.of();
        ReservableOptions reservableOptions = new ReservableOptions(columnOptions.reservable, checkConstraints);
        return new SqlColumnMapping(column, dataType, dbType, columnOptions.length, columnOptions.precision, columnOptions.scale, required, autoGenerated,
            generatedValueType, definition, columnOptions.jsonDataType, reservableOptions);
    }

    private static void validateReservableProperty(PersistentProperty property,
                                                   boolean primaryKey,
                                                   boolean foreign,
                                                   Dialect dialect,
                                                   @Nullable String definition) {
        if (dialect != Dialect.ORACLE) {
            throw new MappingException("@Reservable property [" + property.getOwner().getName() + "." + property.getName() + "] is only supported for Oracle");
        }
        if (!property.getDataType().isNumeric()) {
            throw new MappingException("@Reservable property [" + property.getOwner().getName() + "." + property.getName() + "] must be numeric");
        }
        if (primaryKey) {
            throw new MappingException("@Reservable property [" + property.getOwner().getName() + "." + property.getName() + "] cannot be a primary key");
        }
        if (property.isGenerated()) {
            throw new MappingException("@Reservable property [" + property.getOwner().getName() + "." + property.getName() + "] cannot be generated");
        }
        if (foreign) {
            throw new MappingException("@Reservable property [" + property.getOwner().getName() + "." + property.getName() + "] cannot be a foreign key");
        }
        if (StringUtils.isNotEmpty(definition)) {
            throw new MappingException("@Reservable property [" + property.getOwner().getName() + "." + property.getName() + "] cannot use a custom column definition");
        }
    }

    private static void validateReservableColumns(PersistentEntity entity,
                                                  List<SqlColumnMapping> primaryKeyColumns,
                                                  List<SqlColumnMapping> columns,
                                                  List<SqlIndexMapping> indexes) {
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property instanceof Association && property.getAnnotationMetadata().hasAnnotation(Reservable.class)) {
                throw new MappingException("@Reservable property [" + property.getOwner().getName() + "." + property.getName() + "] cannot be a relationship property");
            }
        }
        List<SqlColumnMapping> reservableColumns = columns.stream().filter(SqlColumnMapping::isReservable).toList();
        if (reservableColumns.isEmpty()) {
            return;
        }
        if (CollectionUtils.isEmpty(primaryKeyColumns)) {
            throw new MappingException("Entity [" + entity.getName() + "] with @Reservable properties must have a primary key");
        }
        if (reservableColumns.size() > 10) {
            throw new MappingException("Entity [" + entity.getName() + "] cannot declare more than 10 @Reservable properties");
        }
        Set<String> reservableNames = reservableColumns.stream().map(SqlColumnMapping::getName).collect(Collectors.toSet());
        for (PersistentProperty property : entity.getPersistentProperties()) {
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), property, (associations, persistentProperty) -> {
                if (persistentProperty.getAnnotationMetadata().hasAnnotation(Reservable.class)
                    && persistentProperty.getAnnotationMetadata().hasAnnotation(Index.class)) {
                    throw new MappingException("@Reservable property [" + persistentProperty.getOwner().getName() + "." + persistentProperty.getName() + "] cannot be indexed");
                }
            });
        }
        indexes.stream()
            .flatMap(index -> Arrays.stream(index.columns()))
            .filter(reservableNames::contains)
            .findFirst()
            .ifPresent(column -> {
                throw new MappingException("@Reservable column [" + column + "] of entity [" + entity.getName() + "] cannot be indexed");
            });
    }

    private static List<SqlCheckConstraint> deriveNumericChecks(PersistentProperty property, String columnName, String tableName) {
        AnnotationMetadata annotationMetadata = property.getAnnotationMetadata();
        List<SqlCheckConstraint> constraints = new ArrayList<>();
        if (hasConstraint(annotationMetadata, JAKARTA_POSITIVE, JAVAX_POSITIVE)) {
            addCheck(constraints, columnName, tableName, ">", "0");
        }
        if (hasConstraint(annotationMetadata, JAKARTA_POSITIVE_OR_ZERO, JAVAX_POSITIVE_OR_ZERO)) {
            addCheck(constraints, columnName, tableName, ">=", "0");
        }
        if (hasConstraint(annotationMetadata, JAKARTA_NEGATIVE, JAVAX_NEGATIVE)) {
            addCheck(constraints, columnName, tableName, "<", "0");
        }
        if (hasConstraint(annotationMetadata, JAKARTA_NEGATIVE_OR_ZERO, JAVAX_NEGATIVE_OR_ZERO)) {
            addCheck(constraints, columnName, tableName, "<=", "0");
        }
        getConstraintAnnotations(annotationMetadata, JAKARTA_MIN, JAVAX_MIN).stream()
            .flatMap(annotation -> annotation.longValue(VALUE_MEMBER).stream().mapToObj(String::valueOf))
            .forEach(value -> addCheck(constraints, columnName, tableName, ">=", value));
        getConstraintAnnotations(annotationMetadata, JAKARTA_MAX, JAVAX_MAX).stream()
            .flatMap(annotation -> annotation.longValue(VALUE_MEMBER).stream().mapToObj(String::valueOf))
            .forEach(value -> addCheck(constraints, columnName, tableName, "<=", value));
        getConstraintAnnotations(annotationMetadata, JAKARTA_DECIMAL_MIN, JAVAX_DECIMAL_MIN)
            .forEach(annotation -> addCheck(constraints, columnName, tableName, annotation.booleanValue("inclusive").orElse(true) ? ">=" : ">",
                annotation.stringValue(VALUE_MEMBER).orElse("0")));
        getConstraintAnnotations(annotationMetadata, JAKARTA_DECIMAL_MAX, JAVAX_DECIMAL_MAX)
            .forEach(annotation -> addCheck(constraints, columnName, tableName, annotation.booleanValue("inclusive").orElse(true) ? "<=" : "<",
                annotation.stringValue(VALUE_MEMBER).orElse("0")));
        return constraints;
    }

    private static boolean hasConstraint(AnnotationMetadata annotationMetadata, String jakartaName, String javaxName) {
        return !getConstraintAnnotations(annotationMetadata, jakartaName, javaxName).isEmpty();
    }

    private static List<AnnotationValue<Annotation>> getConstraintAnnotations(AnnotationMetadata annotationMetadata,
                                                                                String jakartaName,
                                                                                String javaxName) {
        List<AnnotationValue<Annotation>> annotations = new ArrayList<>();
        addConstraintAnnotations(annotations, annotationMetadata, jakartaName);
        addConstraintAnnotations(annotations, annotationMetadata, javaxName);
        return annotations;
    }

    private static void addConstraintAnnotations(List<AnnotationValue<Annotation>> annotations,
                                                 AnnotationMetadata annotationMetadata,
                                                 String annotationName) {
        annotationMetadata.findAnnotation(annotationName).ifPresent(annotations::add);
        annotationMetadata.findAnnotation(annotationName + LIST_ANNOTATION_SUFFIX)
            .ifPresent(container -> annotations.addAll(container.getAnnotations(VALUE_MEMBER)));
    }

    private static void addCheck(List<SqlCheckConstraint> constraints, String columnName, String tableName, String operator, String value) {
        SqlCheckConstraint constraint = new SqlCheckConstraint(
            constraintName(columnName, tableName, operator, value),
            operator,
            value
        );
        if (!constraints.contains(constraint)) {
            constraints.add(constraint);
        }
    }

    private static String constraintName(String columnName, String tableName, String operator, String value) {
        String sanitizedValue;
        if (value.startsWith("-")) {
            sanitizedValue = "NEG_" + sanitize(value.substring(1));
        } else if (value.startsWith("+")) {
            sanitizedValue = "POS_" + sanitize(value.substring(1));
        } else {
            sanitizedValue = sanitize(value);
        }
        String name = "CK_" + sanitize(tableName) + "_" + sanitize(columnName) + "_" + opToken(operator) + "_" + sanitizedValue;
        if (name.length() <= MAX_CONSTRAINT_NAME_LENGTH) {
            return name;
        }
        String hash = constraintNameHash(name);
        return name.substring(0, MAX_CONSTRAINT_NAME_LENGTH - hash.length() - 1) + "_" + hash;
    }

    private static String constraintNameHash(String name) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(CONSTRAINT_NAME_HASH_LENGTH);
            for (byte value : digest) {
                hash.append(String.format("%02x", value & 0xff));
                if (hash.length() >= CONSTRAINT_NAME_HASH_LENGTH) {
                    return hash.substring(0, CONSTRAINT_NAME_HASH_LENGTH);
                }
            }
            throw new IllegalStateException("SHA-256 digest is shorter than expected");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String sanitize(String value) {
        String sanitized = value.toUpperCase(java.util.Locale.ENGLISH).replaceAll("[^A-Z0-9]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        if (sanitized.startsWith("_")) {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.endsWith("_")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        return sanitized.isEmpty() ? "X" : sanitized;
    }

    private static String opToken(String operator) {
        return switch (operator) {
            case ">" -> "GT";
            case ">=" -> "GE";
            case "<" -> "LT";
            case "<=" -> "LE";
            default -> "OP";
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
    private static SqlDbType getDbType(PersistentProperty property, @Nullable String definition) {
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
            case DURATION -> SqlDbType.DURATION;
            case PERIOD -> SqlDbType.PERIOD;
            case STRING_ARRAY, CHARACTER_ARRAY, SHORT_ARRAY, INTEGER_ARRAY,
                LONG_ARRAY, FLOAT_ARRAY, DOUBLE_ARRAY, BOOLEAN_ARRAY, UUID_ARRAY -> SqlDbType.ARRAY;
            default -> {
                if (property.isEnum()) {
                    yield SqlDbType.ENUM;
                } else if (property.isAssignable(Clob.class)) {
                    yield SqlDbType.CLOB;
                } else if (property.isAssignable(Blob.class)) {
                    yield SqlDbType.BLOB;
                } else if (definition != null && !definition.isEmpty()) {
                    yield SqlDbType.JAVA_OBJECT;
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
            if (association.getKind() != Relation.Kind.EMBEDDED && foreignAssociation == null) {
                foreignAssociation = association;
            }
        }
        if (foreignAssociation != null) {
            return foreignAssociation.isRequired();
        }
        return property.isRequired();
    }

    private static List<SqlSequenceMapping> getSqlSequenceMappings(List<PersistentProperty> identities) {
        List<SqlSequenceMapping> sequences = new ArrayList<>();
        for (PersistentProperty identity : identities) {
            if (identity.isGenerated()) {
                GeneratedValue.Type idGeneratorType = identity.getAnnotationMetadata()
                    .enumValue(GeneratedValue.class, GeneratedValue.Type.class)
                    .orElse(null);
                final String generatedDefinition = identity.getAnnotationMetadata().stringValue(GeneratedValue.class, "definition").orElse(null);
                final String definedSequenceName = identity.getAnnotationMetadata().stringValue(GeneratedValue.class, "ref").orElse(null);
                sequences.add(new SqlSequenceMapping(generatedDefinition, definedSequenceName, identity.getDataType(), Optional.ofNullable(idGeneratorType)));
            }
        }
        return sequences;
    }

    private static List<String> getAuxiliaryStatements(PersistentEntity entity, String tableName, NamingStrategy namingStrategy, Dialect dialect) {
        if (dialect != Dialect.ORACLE) {
            return List.of();
        }
        List<String> statements = new ArrayList<>();
        for (PersistentProperty property : entity.getPersistentProperties()) {
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), property, (associations, persistentProperty) -> {
                if (persistentProperty.isAssignable(Geometry.class)) {
                    int srid = 0;
                    AnnotationMetadata annotationMetadata = persistentProperty.getAnnotationMetadata();
                    OptionalInt sridOpt = annotationMetadata.intValue(Srid.class);
                    if (sridOpt.isPresent()) {
                        srid = sridOpt.getAsInt();
                    } else if (annotationMetadata.hasAnnotation(Index.class)) {
                        srid = SRID_WGS_84;
                    }
                    String columnName = namingStrategy.mappedName(associations, persistentProperty);
                    if (srid == SRID_WGS_84 || srid == SRID_ETRS_89) {
                        statements.add(ORACLE_GEOM_METADATA_STATEMENT.formatted(tableName, columnName, "-180", "180", "0.005", "-90", "90", "0.005", srid));
                    } else if (srid == SRID_WEB_MERCATOR) {
                        statements.add(ORACLE_GEOM_METADATA_STATEMENT.formatted(tableName, columnName, "-20037508.3427892", "20037508.3427892", "0.001", "-20037508.3427892", "20037508.3427892", "0.001", srid));
                    }
                }
            });
        }
        return statements;
    }

    private static List<SqlIndexMapping> getSqlIndexMappings(PersistentEntity entity,
                                                             Dialect dialect,
                                                             List<SqlIndexDefinitionProvider> sqlIndexDefinitionProviders) {
        NamingStrategy namingStrategy = entity.getNamingStrategy();
        Set<SqlIndexMapping> indexMappings = new LinkedHashSet<>();
        addSqlIndexMappings(entity, namingStrategy, Collections.emptyList(), indexMappings, dialect, sqlIndexDefinitionProviders);
        return new ArrayList<>(indexMappings);
    }

    @SuppressWarnings("java:S3776")
    private static void addSqlIndexMappings(PersistentEntity entity,
                                            NamingStrategy namingStrategy,
                                            List<Association> associations,
                                            Set<SqlIndexMapping> indexMappings,
                                            Dialect dialect,
                                            List<SqlIndexDefinitionProvider> sqlIndexDefinitionProviders) {
        Map<String, PersistentProperty> propertyMap = entity.getPersistentProperties().stream()
            .filter(pp -> !(pp instanceof Association a && a.isForeignKey()))
            .collect(Collectors.toMap(namingStrategy::mappedName, Function.identity()));

        final Optional<List<AnnotationValue<Index>>> indexes = entity
            .findAnnotation(Indexes.class)
            .map(idxes -> idxes.getAnnotations(VALUE_MEMBER, Index.class));

        Stream.of(indexes)
            .flatMap(Optional::stream)
            .flatMap(Collection::stream)
            .map(index -> toSqlIndexMapping(index, propertyMap, namingStrategy, associations))
            .forEach(indexMappings::add);

        for (PersistentProperty prop : entity.getPersistentProperties()) {
            AnnotationValue<VectorIndex> vi = prop.getAnnotationMetadata().getAnnotation(VectorIndex.class);
            if (vi != null) {
                String name = vi.stringValue("name").orElse("");
                VectorIndexType indexType = vi.enumValue("vectorIndexType", VectorIndexType.class).orElse(VectorIndexType.IVF);
                VectorIndexType.DistanceType distanceType = vi.enumValue("distanceType", VectorIndexType.DistanceType.class).orElse(VectorIndexType.DistanceType.COSINE);
                int accuracy = vi.intValue("accuracy").orElse(90);
                boolean sparse = VectorShape.isSparse(prop.getAnnotationMetadata());
                String columnName = namingStrategy.mappedName(associations, prop);
                VectorIndexMetadata meta = new VectorIndexMetadata(indexType, distanceType, accuracy, sparse);
                SqlIndexDefinitionProvider provider = null;
                io.micronaut.core.type.Argument<?> arg;
                if (prop instanceof io.micronaut.data.model.runtime.RuntimePersistentProperty<?> rpp) {
                    arg = rpp.getArgument();
                } else {
                    arg = io.micronaut.core.type.Argument.of(Object.class);
                }
                for (SqlIndexDefinitionProvider sqlIndexDefinitionProvider: sqlIndexDefinitionProviders) {
                    try {
                        if (sqlIndexDefinitionProvider.supports(arg, dialect)) {
                            provider = sqlIndexDefinitionProvider;
                            break;
                        }
                    } catch (Exception e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Ignoring SqlIndexDefinitionProvider [{}] failure while resolving index for property [{}]",
                                sqlIndexDefinitionProvider.getClass().getName(), prop.getName(), e);
                        }
                    }
                }
                if (provider == null) {
                    throw new MappingException("Vector indexes are not supported for dialect " + dialect + " on property: " + prop.getName());
                }
                indexMappings.add(new SqlIndexMapping(name, false, new String[]{columnName}, provider, meta, false));
            }
        }

        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property instanceof Association association && association.getKind() == Relation.Kind.EMBEDDED) {
                PersistentEntity embeddedEntity = association.getAssociatedEntity();
                List<Association> newAssociations = new ArrayList<>(associations);
                newAssociations.add(association);
                addSqlIndexMappings(embeddedEntity, namingStrategy, newAssociations, indexMappings, dialect, sqlIndexDefinitionProviders);
            }
        }
    }

    private static SqlIndexMapping toSqlIndexMapping(AnnotationValue<Index> index,
                                                     Map<String, PersistentProperty> propertyMap,
                                                     NamingStrategy namingStrategy,
                                                     List<Association> associations) {
        String name = index.stringValue("name").orElse("");
        boolean unique = index.booleanValue("unique").orElse(false);
        String[] declaredColumns = index.stringValues("columns");
        boolean spatial = false;
        Integer spatialSrid = null;
        String[] mappedColumns = new String[declaredColumns.length];
        for (int i = 0; i < declaredColumns.length; i++) {
            String declaredColumn = declaredColumns[i];
            PersistentProperty persistentProperty = propertyMap.get(declaredColumn);
            if (persistentProperty == null) {
                throw new MappingException("Persistent property not found for column: " + declaredColumn);
            }
            if (persistentProperty.isAssignable(Geometry.class)) {
                OptionalInt optSrid = persistentProperty.getAnnotationMetadata().intValue(Srid.class);
                if (optSrid.isPresent()) {
                    spatialSrid = optSrid.getAsInt();
                }
                spatial = true;
            }
            mappedColumns[i] = namingStrategy.mappedName(associations, persistentProperty);
        }
        if (spatial && mappedColumns.length > 1) {
            throw new MappingException("A geospatial column cannot be included in a composite index. Index columns: " + Arrays.toString(mappedColumns));
        }
        return new SqlIndexMapping(name, unique, mappedColumns, spatial, spatialSrid);
    }

    private static List<SqlColumnMapping> getPrimaryKeyColumns(List<SqlColumnDefinitionProvider> columnDefinitionProviders,
                                                               List<PersistentProperty> identities,
                                                               NamingStrategy namingStrategy,
                                                               String tableName,
                                                               Dialect dialect) {
        List<SqlColumnMapping> primaryKeyColumns = new ArrayList<>(identities.size());
        for (PersistentProperty identity : identities) {
            List<PersistentPropertyPath> ids = new ArrayList<>();
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property)
                -> ids.add(PersistentPropertyPath.of(associations, property, "")));
            for (PersistentPropertyPath pp : ids) {
                String columnName = namingStrategy.mappedName(pp.getAssociations(), pp.getProperty());
                SqlColumnMapping column = getColumnDefinition(columnDefinitionProviders, pp.getProperty(), columnName, tableName, true,
                    isRequired(pp.getAssociations(), pp.getProperty()), !SqlQueryBuilderUtils.isNotForeign(pp.getAssociations()), dialect);
                primaryKeyColumns.add(column);
            }
        }
        return primaryKeyColumns;
    }

    private record ColumnOptions(@Nullable Integer length,
                                 @Nullable Integer precision,
                                 @Nullable Integer scale,
                                 @Nullable JsonDataType jsonDataType,
                                 boolean reservable,
                                 PersistentProperty property) {
    }
}
