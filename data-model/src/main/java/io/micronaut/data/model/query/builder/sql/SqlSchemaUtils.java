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
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.SqlDbType;
import io.micronaut.data.model.schema.sql.SqlIndexMapping;
import io.micronaut.data.model.schema.sql.SqlSequenceMapping;
import io.micronaut.data.model.schema.sql.SqlTableMapping;

import java.lang.annotation.Annotation;
import java.sql.Blob;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
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
    @SuppressWarnings("java:S3776")
    public static List<SqlTableMapping> getSqlTableMappings(PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);

        final String tableName = entity.getPersistedName();
        String schema = SqlQueryBuilderUtils.getSchemaName(entity);
        boolean escape = entity.getAnnotationMetadata().booleanValue(MappedEntity.class, "escape").orElse(true);

        List<SqlTableMapping> tables = new ArrayList<>();

        Collection<Association> foreignKeyAssociations = SqlQueryBuilderUtils.getJoinTableAssociations(entity);

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
                addJoinTableColumns(entity, namingStrategy, leftProperties, leftJoinTableColumns, joinColumns);
                addJoinTableColumns(entity, namingStrategy, rightProperties, rightJoinTableColumns, joinColumns);
                SqlTableMapping joinTable = new SqlTableMapping(joinTableSchema, joinTableName, escape, SqlTableMapping.TableType.JOIN, joinColumns, Collections.emptyList());
                tables.add(joinTable);
            }
        }

        List<PersistentProperty> identities = entity.getIdentityProperties();
        List<SqlColumnMapping> primaryKeyColumns = getPrimaryKeyColumns(identities, namingStrategy);

        List<SqlColumnMapping> columns = new ArrayList<>();

        if (entity.hasVersion()) {
            PersistentProperty version = entity.getVersion();
            if (!version.isGenerated()) {
                String columnName = namingStrategy.mappedName(Collections.emptyList(), version);
                SqlColumnMapping column = getColumnDefinition(version, columnName, false, true, false);
                columns.add(column);
            }
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

        List<SqlSequenceMapping> sequences = getSqlSequenceMappings(identities);
        List<SqlIndexMapping> indexes = getSqlIndexMappings(entity, namingStrategy);

        SqlTableMapping table = new SqlTableMapping(schema, tableName, escape, SqlTableMapping.TableType.MAIN, primaryKeyColumns, columns, sequences,
            indexes);
        tables.add(table);
        return tables;
    }

    /**
     * Create Join table columns.
     *
     * @param entity The entity
     * @param namingStrategy The naming strategy
     * @param joinProperties The properties that are used for joining (typically left or right identity)
     * @param joinColumns The corresponding columns that are used for joining
     * @param joinTableColumns The resulting columns used for joing table
     */
    private static void addJoinTableColumns(PersistentEntity entity, NamingStrategy namingStrategy, List<PersistentPropertyPath> joinProperties, List<String> joinColumns, List<SqlColumnMapping> joinTableColumns) {
        if (joinColumns.size() == joinProperties.size()) {
            for (int i = 0; i < joinColumns.size(); i++) {
                PersistentPropertyPath pp = joinProperties.get(i);
                String columnName = joinColumns.get(i);
                joinTableColumns.add(getColumnDefinition(pp.getProperty(), columnName, true, true, true));
            }
        } else {
            for (PersistentPropertyPath pp : joinProperties) {
                String columnName = namingStrategy.mappedJoinTableColumn(entity, pp.getAssociations(), pp.getProperty());
                joinTableColumns.add(getColumnDefinition(pp.getProperty(), columnName, true, true, true));
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
            .orElse(AUTO) : AUTO;
        OptionalInt optPrecision = SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "precision");
        OptionalInt optScale = SqlQueryBuilderUtils.findPersistenceColumnValue(annotationMetadata, "scale");

        if (annotationMetadata.hasAnnotation(JsonAnyGetter.class) || annotationMetadata.hasAnnotation(JsonAnySetter.class)) {
            return new SqlColumnMapping(column, dataType, SqlDbType.JSON_OBJECT, false, null, false, false, generatedValueType, definition);
        }

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
            case JSON -> new SqlColumnMapping(column, dataType, dbType, null, null, null, required, autoGenerated, generatedValueType,
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
                yield new SqlColumnMapping(column, dataType, dbType, null, precision, scale, required, autoGenerated, generatedValueType,
                    definition, null);
            }
            case DURATION, PERIOD -> new SqlColumnMapping(column, dataType, dbType, null, null, null, required, autoGenerated, generatedValueType,
                definition, null);
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
            case DURATION -> SqlDbType.DURATION;
            case PERIOD -> SqlDbType.PERIOD;
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

    private static List<SqlIndexMapping> getSqlIndexMappings(PersistentEntity entity,
                                                             NamingStrategy namingStrategy) {
        Set<SqlIndexMapping> indexMappings = new LinkedHashSet<>();
        addSqlIndexMappings(entity, namingStrategy, Collections.emptyList(), indexMappings);
        return new ArrayList<>(indexMappings);
    }

    private static void addSqlIndexMappings(PersistentEntity entity,
                                            NamingStrategy namingStrategy,
                                            List<Association> associations,
                                            Set<SqlIndexMapping> indexMappings) {
        Map<String, PersistentProperty> propertyMap = entity.getPersistentProperties().stream()
            .filter(pp -> !(pp instanceof Association a && a.isForeignKey()))
            .collect(Collectors.toMap(namingStrategy::mappedName, Function.identity()));

        UnaryOperator<String> columnMapper = columnName ->
            Optional.ofNullable(propertyMap.get(columnName))
                .map(pp -> namingStrategy.mappedName(associations, pp))
                .orElseThrow(() -> new MappingException("Persistent property not found for column: " + columnName));

        final Optional<List<AnnotationValue<Index>>> indexes = entity
            .findAnnotation(Indexes.class)
            .map(idxes -> idxes.getAnnotations(VALUE_MEMBER, Index.class));

        Stream.of(indexes)
            .flatMap(Optional::stream)
            .flatMap(Collection::stream)
            .forEach(index -> {
                String name = index.stringValue("name").orElse("");
                boolean unique = index.booleanValue("unique").orElse(false);
                String[] declaredColumns = index.stringValues("columns");
                String[] mappedColumns = Arrays.stream(declaredColumns)
                    .map(columnMapper)
                    .toArray(String[]::new);
                indexMappings.add(new SqlIndexMapping(name, unique, mappedColumns));
            });

        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property instanceof Association association && association.getKind() == Relation.Kind.EMBEDDED) {
                PersistentEntity embeddedEntity = association.getAssociatedEntity();
                List<Association> newAssociations = new ArrayList<>(associations);
                newAssociations.add(association);
                addSqlIndexMappings(embeddedEntity, namingStrategy, newAssociations, indexMappings);
            }
        }
    }

    private static List<SqlColumnMapping> getPrimaryKeyColumns(List<PersistentProperty> identities, NamingStrategy namingStrategy) {
        List<SqlColumnMapping> primaryKeyColumns = new ArrayList<>(identities.size());
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
        return primaryKeyColumns;
    }
}
