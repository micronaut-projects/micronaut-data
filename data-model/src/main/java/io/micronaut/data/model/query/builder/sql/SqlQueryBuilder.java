/*
 * Copyright 2017-2020 original authors
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
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.Relation.Kind;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.convert.GeometryWktConverter;
import jakarta.persistence.criteria.JoinType;
import io.micronaut.data.model.runtime.convert.DefinitionProvider;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.EntityRepresentation;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Srid;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.PersistentAssociationPath;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.DefaultPersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.DefaultOrder;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryOutParameterBinding;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.runtime.convert.SqlIndexDefinitionProvider;
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.SqlDbType;
import io.micronaut.data.model.schema.sql.SqlIndexMapping;
import io.micronaut.data.model.schema.sql.SqlSequenceMapping;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.micronaut.data.annotation.GeneratedValue.Type.AUTO;
import static io.micronaut.data.annotation.GeneratedValue.Type.IDENTITY;
import static io.micronaut.data.annotation.GeneratedValue.Type.SEQUENCE;
import static io.micronaut.data.annotation.GeneratedValue.Type.UUID;

/**
 * Implementation of {@link QueryBuilder} that builds SQL queries.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
@Internal
@SuppressWarnings("FileLength")
public class SqlQueryBuilder extends AbstractSqlLikeQueryBuilder {

    /**
     * The start of an IN expression.
     */
    public static final String DEFAULT_POSITIONAL_PARAMETER_MARKER = "?";

    public static final String STANDARD_FOR_UPDATE_CLAUSE = " FOR UPDATE";
    public static final String SQL_SERVER_FOR_UPDATE_CLAUSE = " WITH (UPDLOCK, ROWLOCK)";

    private static final String VALUE_MEMBER = "value";
    private static final String BLANK_SPACE = " ";
    private static final String INSERT_INTO = "INSERT INTO ";
    private static final String JDBC_REPO_ANNOTATION = "io.micronaut.data.jdbc.annotation.JdbcRepository";
    private static final String DIALECT_ATTR = "dialect";
    private static final String REFERENCED_COLUMN_NAME = "referencedColumnName";

    private static final Logger LOG = LoggerFactory.getLogger(SqlQueryBuilder.class);

    // Shared, stateless no-op predicate to avoid per-call allocations in createQueryState().predicate()
    private static final Predicate EMPTY_PREDICATE = new RenderablePredicate() {
        @Override
        void render(StringBuilder query, PropertyParameterCreator propertyParameterCreator) {
            // no-op: intentionally renders nothing
        }

        @Override
        public String toString() {
            return "RenderablePredicate.EMPTY";
        }
    };

    private final Dialect dialect;
    private final Map<Dialect, DialectConfig> perDialectConfig = new EnumMap<>(Dialect.class);

    /**
     * Constructor with annotation metadata.
     *
     * @param annotationMetadata The annotation metadata
     */
    @Creator
    public SqlQueryBuilder(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata != null) {
            this.dialect = annotationMetadata
                .enumValue(JDBC_REPO_ANNOTATION, DIALECT_ATTR, Dialect.class)
                .orElseGet(() ->
                    annotationMetadata
                        .enumValue(Repository.class, DIALECT_ATTR, Dialect.class)
                        .orElse(Dialect.ANSI));

            AnnotationValue<SqlQueryConfiguration> annotation = annotationMetadata.getAnnotation(SqlQueryConfiguration.class);
            if (annotation != null) {
                List<AnnotationValue<SqlQueryConfiguration.DialectConfiguration>> dialectConfigs = annotation.getAnnotations(AnnotationMetadata.VALUE_MEMBER, SqlQueryConfiguration.DialectConfiguration.class);
                for (AnnotationValue<SqlQueryConfiguration.DialectConfiguration> dialectConfig : dialectConfigs) {
                    dialectConfig.enumValue(DIALECT_ATTR, Dialect.class).ifPresent(aDialect -> {
                        DialectConfig dc = new DialectConfig();
                        perDialectConfig.put(aDialect, dc);
                        dialectConfig.stringValue("positionalParameterFormat").ifPresent(format ->
                            dc.positionalFormatter = format);
                        dialectConfig.stringValue("positionalParameterName").ifPresent(format ->
                            dc.positionalNameFormatter = format);
                        dialectConfig.booleanValue("escapeQueries").ifPresent(escape ->
                            dc.escapeQueries = escape);
                    });

                }
            }
        } else {
            this.dialect = Dialect.ANSI;
        }
    }

    /**
     * Default constructor.
     */
    public SqlQueryBuilder() {
        this.dialect = Dialect.ANSI;
    }

    /**
     * @param dialect The dialect
     */
    public SqlQueryBuilder(Dialect dialect) {
        ArgumentUtils.requireNonNull(DIALECT_ATTR, dialect);
        this.dialect = dialect;
    }

    /**
     * @return The dialect being used by the builder.
     */
    @Override
    public Dialect getDialect() {
        return dialect;
    }

    @Override
    protected boolean shouldEscape(PersistentEntity entity) {
        Boolean shouldEscapeDialect = shouldEscapeDialect(dialect);
        return Objects.requireNonNullElseGet(shouldEscapeDialect, () -> super.shouldEscape(entity));
    }

    private @Nullable Boolean shouldEscapeDialect(Dialect dialect) {
        DialectConfig config = perDialectConfig.get(dialect);
        if (config != null && config.escapeQueries != null) {
            return config.escapeQueries;
        }
        return null;
    }

    @Override
    protected String asLiteral(@Nullable Object value) {
        if ((dialect == Dialect.SQL_SERVER || dialect == Dialect.ORACLE) && value instanceof Boolean vBoolean) {
            return vBoolean ? "1" : "0";
        }
        return super.asLiteral(value);
    }

    /**
     * Builds a batch create tables statement. Designed for testing and not production usage. For production a
     * SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entities the entities
     * @return The table
     */
    @Experimental
    public String buildBatchCreateTableStatement(PersistentEntity... entities) {
        return buildBatchCreateTableStatement(List.of(), entities);
    }

    /**
     * Builds a batch create tables statement. Designed for testing and not production usage. For production a
     * SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param columnDefinitionProviders the list of SqlColumnDefinitionProvider
     * @param entities the entities
     * @return The table
     */
    @Experimental
    public String buildBatchCreateTableStatement(List<DefinitionProvider> columnDefinitionProviders,
                                                 PersistentEntity... entities) {
        return Arrays.stream(entities)
            .flatMap(entity -> Stream.of(buildCreateTableStatements(entity, columnDefinitionProviders)))
            .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Builds a batch drop tables statement. Designed for testing and not production usage. For production a
     * SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entities the entities
     * @return The table
     */
    @Experimental
    public String buildBatchDropTableStatement(PersistentEntity... entities) {
        return Arrays.stream(entities).flatMap(entity -> Stream.of(buildDropTableStatements(entity)))
            .collect(Collectors.joining("\n"));
    }

    /**
     * Builds the drop table statement. Designed for testing and not production usage. For production a
     * SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entity The entity
     * @return The tables for the give entity
     */
    @Experimental
    public String[] buildDropTableStatements(PersistentEntity entity) {
        List<String> dropStatements = new ArrayList<>();
        if (entity.getAnnotationMetadata().hasAnnotation(JsonView.class)) {
            String sql = "DROP VIEW " + getTableName(entity);
            dropStatements.add(sql);
            return dropStatements.toArray(new String[0]);
        }
        String tableName = getTableName(entity);
        boolean escape = shouldEscape(entity);
        String sql = "DROP TABLE " + tableName;
        Collection<Association> foreignKeyAssociations = SqlQueryBuilderUtils.getJoinTableAssociations(entity);
        for (Association association : foreignKeyAssociations) {
            AnnotationMetadata associationMetadata = association.getAnnotationMetadata();
            NamingStrategy namingStrategy = getNamingStrategy(entity);
            String joinTableName = associationMetadata
                .stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, "name")
                .orElseGet(() ->
                    getMappedName(namingStrategy, association));
            dropStatements.add("DROP TABLE " + (escape ? quote(joinTableName, true) : joinTableName) + ";");
        }

        dropStatements.add(sql);
        return dropStatements.toArray(new String[0]);
    }

    /**
     * Builds a join table insert statement for a given entity and association.
     *
     * @param entity      The entity
     * @param association The association
     * @return The join table insert statement
     */

    public String buildJoinTableInsert(PersistentEntity entity,  Association association) {
        if (!isForeignKeyWithJoinTable(association)) {
            throw new IllegalArgumentException("Join table inserts can only be built for foreign key associations that are mapped with a join table.");
        }
        Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
        Association owningAssociation = inverseSide.orElse(association);
        AnnotationMetadata annotationMetadata = owningAssociation.getAnnotationMetadata();
        NamingStrategy namingStrategy = getNamingStrategy(entity);
        String joinTableName = annotationMetadata
            .stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, "name")
            .orElseGet(() ->
                getMappedName(namingStrategy, association));
        joinTableName = quote(joinTableName, true);
        String joinTableSchema = annotationMetadata
            .stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, SqlMembers.SCHEMA)
            .orElse(SqlQueryBuilderUtils.getSchemaName(entity));
        if (StringUtils.isNotEmpty(joinTableSchema)) {
            joinTableSchema = quote(joinTableSchema, true);
            joinTableName = joinTableSchema + DOT + joinTableName;
        }
        List<String> leftJoinColumns = SqlQueryBuilderUtils.resolveJoinTableJoinColumns(annotationMetadata,
            true, entity, namingStrategy);
        List<String> rightJoinColumns = SqlQueryBuilderUtils.resolveJoinTableJoinColumns(annotationMetadata,
            false, association.getAssociatedEntity(), namingStrategy);
        boolean escape = shouldEscape(entity);
        String columns = Stream.concat(leftJoinColumns.stream(), rightJoinColumns.stream())
            .map(columnName -> escape ? quote(columnName) : columnName)
            .collect(Collectors.joining(","));
        String placeholders = IntStream.range(0, leftJoinColumns.size() + rightJoinColumns.size()).mapToObj(i -> formatParameter(i + 1).toString()).collect(Collectors.joining(","));
        return INSERT_INTO + joinTableName + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    /**
     * Is the given association a foreign key reference that requires a join table.
     *
     * @param association The association.
     * @return True if it is.
     */
    public static boolean isForeignKeyWithJoinTable(Association association) {
        return SqlQueryBuilderUtils.isForeignKeyWithJoinTable(association);
    }

    /**
     * Builds a set of {@code CREATE TABLE} statements for the given entity.
     * <p>
     * This method is {@code public} and the class is non-final; therefore it can be overridden.
     * If you override it, ensure you preserve the following behavior expected by callers:
     * <ul>
     *     <li>Return a non-null {@code String[]} containing SQL statements in execution order.</li>
     *     <li>Respect the current {@link #getDialect()} and escaping rules (see {@link #shouldEscape(PersistentEntity)}).</li>
     *     <li>Keep special handling for {@link JsonView} entities (Oracle-only) consistent with the base implementation.</li>
     * </ul>
     *
     * @param entity The entity
     * @return The {@code CREATE TABLE} statements
     */
    @Experimental
    public String[] buildCreateTableStatements(PersistentEntity entity) {
        return buildCreateTableStatements(entity, List.of());
    }

    /**
     * Builds a set of {@code CREATE TABLE} statements for the given entity.
     *
     * @param entity The entity
     * @param definitionProviders The definition providers
     * @return The {@code CREATE TABLE} statements
     */
    @Experimental
    public String[] buildCreateTableStatements(PersistentEntity entity, List<DefinitionProvider> definitionProviders) {
        List<String> createStatements = new ArrayList<>();
        if (entity.getAnnotationMetadata().hasAnnotation(JsonView.class)) {
            if (dialect != Dialect.ORACLE) {
                LOG.error("JSON View is not supported for dialect " + dialect);
                return StringUtils.EMPTY_STRING_ARRAY;
            }
            addJsonViewCreateStatement(createStatements, entity);
            return createStatements.toArray(new String[0]);
        }
        List<SqlTableMapping> tables = SqlSchemaUtils.getSqlTableMappings(definitionProviders, entity, getDialect());
        assert CollectionUtils.isNotEmpty(tables);
        boolean escape = shouldEscape(entity);
        String schema = SqlQueryBuilderUtils.getSchemaName(entity);

        if (StringUtils.isNotEmpty(schema)) {
            createStatements.add("CREATE SCHEMA " + (escape ? quote(schema, true) : schema) + ";");
        }

        for (SqlTableMapping table : tables) {
           addTableCreateStatements(createStatements, table, schema, escape);
        }

        return createStatements.toArray(new String[0]);
    }

    /**
     * Builds the creation table statement for collection of entities. Designed for testing and not production usage. For production a
     * SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entities The collection of entities
     * @return The tables for the given entities
     */
    @Experimental
    public final String[] buildCreateTableStatements(PersistentEntity... entities) {
        return buildCreateTableStatements(entities, getDialect());
    }

    @Experimental
    public final String[] buildCreateTableStatements(PersistentEntity[] entities, Dialect dialect) {
        return buildCreateTableStatements(List.of(), entities, dialect);
    }

    @Experimental
    public final String[] buildCreateTableStatements(List<DefinitionProvider> definitionProviders,
                                                     PersistentEntity[] entities,
                                                     Dialect dialect) {
        Map<String, SqlTableMapping> sqlTableMappingByTableName = CollectionUtils.newLinkedHashMap(entities.length);
        // Entity can generate indexes, sequences, join tables so need some longer map
        List<String> createStatements = new ArrayList<>(entities.length * 5);
        List<String> jsonViewCreateStatements = new ArrayList<>(entities.length);
        for (PersistentEntity entity : entities) {
            String schema = SqlQueryBuilderUtils.getSchemaName(entity);
            boolean escape = shouldEscape(entity);
            if (entity.getAnnotationMetadata().hasAnnotation(JsonView.class)) {
                if (dialect != Dialect.ORACLE) {
                    LOG.error("JSON View is not supported for dialect " + dialect);
                    continue;
                }
                addJsonViewCreateStatement(jsonViewCreateStatements, entity);
                continue;
            }
            List<SqlTableMapping> tables = SqlSchemaUtils.getSqlTableMappings(definitionProviders, entity, dialect);
            if (StringUtils.isNotEmpty(schema)) {
                String createSchemaStatement = "CREATE SCHEMA " + (escape ? quote(schema) : schema) + ";";
                addToCollectionIfNotContains(createStatements, createSchemaStatement);
            }
            for (SqlTableMapping table : tables) {
                addTable(table, sqlTableMappingByTableName);
            }
        }

        for (SqlTableMapping table : sqlTableMappingByTableName.values()) {
            Boolean shouldEscapeDialect = shouldEscapeDialect(dialect);
            boolean escape = Objects.requireNonNullElseGet(shouldEscapeDialect, table::escape);
            addTableCreateStatements(createStatements, table, table.schema(), escape);
        }

        createStatements.addAll(jsonViewCreateStatements);
        return createStatements.toArray(new String[0]);
    }

    private Optional<PersistentEntity> getJsonViewEntity(@NonNull PersistentEntity entity) {
        if (entity.getAnnotationMetadata().hasAnnotation(JsonView.class)) {
            return entity.getAnnotationMetadata().classValue(JsonView.class, "entity").map(c -> PersistentEntity.of(c));
        }
        return Optional.empty();
    }

    private Optional<PersistentEntity> getJsonSubViewEntity(@NonNull PersistentEntity entity) {
        if (entity.getAnnotationMetadata().hasAnnotation(JsonSubView.class)) {
            return entity.getAnnotationMetadata().classValue(JsonSubView.class, "entity").map(c -> PersistentEntity.of(c));
        }
        return Optional.empty();
    }

    private JsonView.Operation[] getViewSupportedOperations(@NonNull PersistentEntity entity) {
        JsonView.Operation[] operations;
        if (entity.getAnnotationMetadata().hasAnnotation(JsonView.class)) {
            operations = entity.getAnnotationMetadata().enumValues(JsonView.class, "operations", JsonView.Operation.class);
        } else {
            operations = entity.getAnnotationMetadata().enumValues(JsonSubView.class, "operations", JsonView.Operation.class);
        }
        if (operations.length == 0) {
            return JsonView.Operation.values();
        }
        return operations;
    }

    private void addJsonViewCreateStatement(List<String> createStatements, PersistentEntity viewEntity) {
        Optional<PersistentEntity> entityOptional = getJsonViewEntity(viewEntity);
        if (entityOptional.isEmpty()) {
            return;
        }
        PersistentEntity entity = entityOptional.get();
        String viewName = viewEntity.getPersistedName();
        if (viewEntity.getAnnotationMetadata().hasAnnotation(MappedEntity.class)) {
            String schema = viewEntity.getAnnotationMetadata().stringValue(MappedEntity.class, "schema").get();
            viewName = schema + "." + viewName;
        }
        StringBuilder sb = new StringBuilder("CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW ")
            .append(viewName)
            .append(AS_CLAUSE)
            .append(SELECT_JSON_CLAUSE)
            .append(OPEN_CURLY_BRACKET);
        createJsonViewQuery(sb, viewEntity, entity, true);
        createStatements.add(sb.toString());
    }

    private boolean isFlexColumn(PersistentProperty column) {
        AnnotationMetadata annotationMetadata = column.getAnnotationMetadata();
        return annotationMetadata.hasAnnotation(JsonAnyGetter.class) || annotationMetadata.hasAnnotation(JsonAnySetter.class);
    }

    private void createJsonViewQuery(StringBuilder sb, PersistentEntity viewEntity, PersistentEntity entity, boolean isTopLevel) {
        String alias = entity.getAliasName();

        List<PersistentProperty> identities = viewEntity.getIdentityProperties();
        Iterator<PersistentProperty> it = identities.iterator();

        List<PersistentProperty> allColumns = (List<PersistentProperty>) viewEntity.getPersistentProperties();
        List<PersistentProperty> columns = allColumns.stream().filter(column -> column.getDataType() != DataType.OBJECT || isFlexColumn(column)).toList();

        while (it.hasNext()) {
            PersistentProperty identity = it.next();
            String viewPropertyName = identity.getAnnotationMetadata().stringValue(SERDE_CONFIG_ANNOTATION, "property")
                .orElse(identity.getAnnotationMetadata().stringValue(JSON_PROPERTY_ANNOTATION)
                    .orElse(identity.getName()));
            String entityPersistedPropertyName = "";
            if (identity.getAnnotationMetadata().hasAnnotation(MappedProperty.class)) {
                entityPersistedPropertyName = identity.getPersistedName();
            } else if (identity.getAnnotationMetadata().hasAnnotation(EmbeddedId.class)) {
                processEmbeddedIdPropertyForJsonView(sb, entity, identity, alias, it, columns, isTopLevel);
                continue;
            } else {
                PersistentProperty property = entity.getPropertyByName(viewPropertyName);
                if (property != null) {
                    entityPersistedPropertyName = property.getPersistedName();
                }
            }
            sb.append("'")
                .append(viewPropertyName)
                .append("': ")
                .append(alias)
                .append(DOT)
                .append(entityPersistedPropertyName);
            if (it.hasNext() || !columns.isEmpty()) {
                sb.append(COMMA)
                    .append(SPACE);
            }
        }

        it = columns.iterator();
        while (it.hasNext()) {
            createJsonViewColumnQuery(sb, it.next(), entity, alias);
            if (it.hasNext()) {
                sb.append(COMMA)
                    .append(SPACE);
            }
        }
        sb.append(CLOSE_CURLY_BRACKET)
            .append(FROM_CLAUSE)
            .append(entity.getPersistedName())
            .append(SPACE)
            .append(alias)
            .append(WITH_CLAUSE);
        JsonView.Operation[] supportedOperations = getViewSupportedOperations(viewEntity);
        for (JsonView.Operation operation: supportedOperations) {
            sb.append(operation).append(SPACE);
        }
    }

    private void createJsonViewColumnQuery(StringBuilder sb, PersistentProperty column, PersistentEntity entity, String alias) {
        AnnotationMetadata annotationMetadata = column.getAnnotationMetadata();
        String columnPropertyName = annotationMetadata.stringValue(SERDE_CONFIG_ANNOTATION, "property")
            .orElse(annotationMetadata.stringValue(JSON_PROPERTY_ANNOTATION)
                .orElse(column.getName()));
        if (column instanceof Association association) {
            processAssociation(sb, association, entity, columnPropertyName);
        } else if (column.getDataType() != DataType.OBJECT) {
            String entityPersistedPropertyName;
            if (annotationMetadata.hasAnnotation(MappedProperty.class)) {
                entityPersistedPropertyName = column.getPersistedName();
            } else {
                PersistentProperty property = entity.getPropertyByName(columnPropertyName);
                if (property == null) {
                    return;
                }
                entityPersistedPropertyName = property.getPersistedName();
            }
            sb.append("'")
                .append(columnPropertyName)
                .append("': ")
                .append(alias)
                .append(DOT)
                .append(entityPersistedPropertyName);
        } else {
            if (isFlexColumn(column)) {
                sb.append(alias)
                    .append(DOT)
                    .append(column.getPersistedName())
                    .append(SPACE)
                    .append(AS_CLAUSE)
                    .append(FLEX_COLUMN);
            }
        }
    }

    private String createJsonEmbeddedProperties(PersistentEntity entity, Association association) {
        PersistentEntity embedded = association.getAssociatedEntity();
        StringBuilder sb = new StringBuilder();
        Iterator<PersistentProperty> properties = ((Collection<PersistentProperty>) embedded.getPersistentProperties()).iterator();
        while (properties.hasNext()) {
            createJsonViewColumnQuery(sb, properties.next(), entity, entity.getAliasName());
            if (properties.hasNext()) {
                sb.append(COMMA).append(SPACE);
            }
        }
        return sb.toString();
    }

    private String createJsonSubViewQuery(Association association) {
        PersistentEntity associatedViewEntity = association.getAssociatedEntity();
        Optional<PersistentEntity> associatedEntityOptional = getJsonSubViewEntity(associatedViewEntity);
        if (associatedEntityOptional.isEmpty()) {
            throw new IllegalStateException("Associated entity not found, set the entity field inside @JsonSubView annotation.");
        }
        PersistentEntity associatedEntity = associatedEntityOptional.get();
        StringBuilder sb = new StringBuilder(SELECT_JSON_CLAUSE).append(OPEN_CURLY_BRACKET);
        createJsonViewQuery(sb, associatedViewEntity, associatedEntity, false);

        PersistentEntity associationOwner = association.getOwner();
        PersistentEntity associationOwnerEntity = null;
        if (associationOwner.getAnnotationMetadata().hasAnnotation(JsonView.class)) {
            Optional<PersistentEntity> associationOwnerEntityOptional = getJsonViewEntity(associationOwner);
            if (associationOwnerEntityOptional.isPresent()) {
                associationOwnerEntity = associationOwnerEntityOptional.get();
            }
        } else {
            Optional<PersistentEntity> associationOwnerEntityOptional = getJsonSubViewEntity(associationOwner);
            if (associationOwnerEntityOptional.isPresent()) {
                associationOwnerEntity = associationOwnerEntityOptional.get();
            }
        }

        if (associationOwnerEntity == null) {
            return "";
        }
        PersistentAssociationPath joinAssociationPath = createAssociationPath(associationOwnerEntity, association);
        QueryState queryState = createQueryState(associatedViewEntity);
        buildJoin(null, sb, queryState, joinAssociationPath, associationOwnerEntity, associatedEntity.getAliasName(), associationOwnerEntity.getAliasName());
        return sb.toString();
    }

    private void processEmbeddedIdPropertyForJsonView(StringBuilder sb, PersistentEntity entity, PersistentProperty identity, String alias, Iterator<PersistentProperty> it, List<PersistentProperty> columns, boolean isTopLevel) {
        if (isTopLevel) {
            sb.append("'_id': {");
        }
        Field[] fields = ((RuntimePersistentProperty<?>) identity).getType().getDeclaredFields();
        List<SqlColumnMapping> columnMappings = SqlSchemaUtils.getSqlTableMappings(entity, getDialect()).getFirst().primaryKeyColumns();
        if (fields.length != columnMappings.size()) {
            throw new IllegalStateException("Declared fields array length (" + fields.length + ") != table mapping primary key array length (" + columnMappings.size() + ").");
        }
        for (int i = 0; i < fields.length; i++) {
            String propertyPersistedName = columnMappings.get(i).getName();
            String propertyName = fields[i].getName();
            sb.append("'")
                .append(propertyName)
                .append("': ")
                .append(alias)
                .append(DOT)
                .append(propertyPersistedName);
            if (i != fields.length - 1) {
                sb.append(COMMA)
                    .append(SPACE);
            }
        }
        if (isTopLevel) {
            sb.append('}');
        }
        if (it.hasNext() || !columns.isEmpty()) {
            sb.append(COMMA)
                .append(SPACE);
        }
    }

    private void processAssociation(StringBuilder sb, Association association, PersistentEntity entity, String columnPropertyName) {
        Relation.Kind kind = association.getKind();
        Optional<PersistentEntity> associatedEntityOptional = getJsonSubViewEntity(association.getAssociatedEntity());
        if (kind != Kind.EMBEDDED && associatedEntityOptional.isEmpty()) {
            throw new IllegalStateException("Associated entity not found, set the entity field inside @JsonSubView annotation of " + association.getAssociatedEntity().getName());
        }
        switch (kind) {
            case ONE_TO_ONE, MANY_TO_ONE -> {
                if (association.getAnnotationMetadata().hasAnnotation(JsonUnwrapped.class)) {
                    sb.append("UNNEST ")
                        .append(OPEN_BRACKET)
                        .append(createJsonSubViewQuery(association))
                        .append(CLOSE_BRACKET);
                } else {
                    sb.append("'")
                        .append(columnPropertyName)
                        .append("': ")
                        .append(OPEN_BRACKET)
                        .append(createJsonSubViewQuery(association))
                        .append(CLOSE_BRACKET);
                }
            }
            case EMBEDDED -> {
                if (association.getAnnotationMetadata().hasAnnotation(JsonUnwrapped.class)) {
                    sb.append(createJsonEmbeddedProperties(entity, association));
                } else {
                    sb.append("'")
                        .append(columnPropertyName)
                        .append("': ")
                        .append(OPEN_BRACKET)
                        .append("JSON ")
                        .append(OPEN_CURLY_BRACKET)
                        .append(createJsonEmbeddedProperties(entity, association))
                        .append(CLOSE_CURLY_BRACKET)
                        .append(CLOSE_BRACKET);
                }
            }
            case ONE_TO_MANY, MANY_TO_MANY -> {
                PersistentEntity associatedEntity = associatedEntityOptional.get();
                if (SqlQueryBuilderUtils.isForeignKeyWithJoinTable(association) && hasEmbeddedId(associatedEntity)) {
                    sb.append(SPACE)
                        .append("UNNEST ")
                        .append(OPEN_BRACKET)
                        .append(SELECT_JSON_CLAUSE)
                        .append(OPEN_CURLY_BRACKET);
                    sb.append("'")
                        .append(columnPropertyName)
                        .append("': [")
                        .append(createJsonSubViewQuery(association))
                        .append("]");
                    sb.append(CLOSE_CURLY_BRACKET);

                    Association joinAssociation = (Association) entity.getPropertyByName(association.getName());
                    if (joinAssociation == null) {
                        throw new IllegalStateException("Join association missing");
                    }

                    String joinTableName = joinAssociation.getAnnotationMetadata()
                        .stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, "name")
                        .orElseGet(() -> getMappedName(entity.getNamingStrategy(), joinAssociation));

                    sb.append(FROM_CLAUSE)
                        .append(joinTableName)
                        .append(SPACE)
                        .append("WITH UPDATE INSERT DELETE");

                    PersistentAssociationPath joinAssociationPath = createAssociationPath(entity, association);
                    QueryState queryState = createQueryState(association.getAssociatedEntity());
                    buildJoin(JoinType.LEFT.name(), sb, queryState, joinAssociationPath, entity, joinAssociation.getAssociatedEntity().getAliasName(), entity.getAliasName());
                    sb.append(CLOSE_BRACKET);
                } else {
                    sb.append("'")
                        .append(columnPropertyName)
                        .append("': [")
                        .append(createJsonSubViewQuery(association))
                        .append("]");
                }
            }
            default -> {
                throw new IllegalStateException("Unsupported relation type");
            }
        }
    }

    private boolean hasEmbeddedId(PersistentEntity entity) {
        return entity.getIdentity().getAnnotationMetadata().hasAnnotation(EmbeddedId.class);
    }

    private void addTable(SqlTableMapping table, Map<String, SqlTableMapping> sqlTableMappingByTableName) {
        boolean addTable = true;
        if (sqlTableMappingByTableName.containsKey(table.name())) {
            SqlTableMapping existingSqlTableMapping = sqlTableMappingByTableName.get(table.name());
            if (table.type() == existingSqlTableMapping.type()) {
                if (LOG.isWarnEnabled() && table.type() == SqlTableMapping.TableType.MAIN) {
                    LOG.warn("Table with name {} has more than one mapped entity. Will use table {}", table.name(), existingSqlTableMapping);
                }
                addTable = false;
            } else if (existingSqlTableMapping.type() == SqlTableMapping.TableType.JOIN) {
                // Remove ad-hoc join table created from one of the entities relation mappings and not an actual entity
                sqlTableMappingByTableName.remove(table.name());
            } else if (table.type() == SqlTableMapping.TableType.JOIN) {
                // Skip this table mapping ad-hoc join table created from one of the entities relation mappings and not an actual entity
                addTable = false;
            }
        }
        if (addTable) {
            sqlTableMappingByTableName.put(table.name(), table);
        }
    }

    private void addTableCreateStatements(List<String> createStatements, SqlTableMapping table, @Nullable String schema, boolean escape) {
        List<String> primaryColumnsName = new ArrayList<>();
        boolean generatePkAfterColumns = false;
        List<String> columns = new ArrayList<>();

        List<SqlColumnMapping> identities = table.primaryKeyColumns();
        if (CollectionUtils.isNotEmpty(identities)) {
            int idFieldCount = identities.size();
            generatePkAfterColumns = idFieldCount > 1;
            if (!generatePkAfterColumns && idFieldCount > 0 && !identities.getFirst().isAutoGenerated()) {
                // Need to define primary key if id not generated (otherwise defined in column definition)
                // but can't do if id field is byte array (BLOB) and it expects length for MySQL
                if (!(dialect == Dialect.MYSQL && identities.getFirst().getDataType() == DataType.BYTE_ARRAY)) {
                    generatePkAfterColumns = true;
                }
            }

            for (SqlColumnMapping tableIdentity : identities) {

                String column = tableIdentity.getName();
                if (escape) {
                    column = quote(column);
                }
                primaryColumnsName.add(column);
                if (StringUtils.isNotEmpty(tableIdentity.getDefinition())) {
                    column += " " + tableIdentity.getDefinition();
                } else {
                    column += " " + tableIdentity.getSqlType(dialect);
                    if (tableIdentity.isRequired()) {
                        column += " NOT NULL";
                    }
                }
                if (tableIdentity.isAutoGenerated()) {
                    column = addGeneratedStatementToColumn(tableIdentity.getGeneratedValueType(), tableIdentity.getDataType(), column, !generatePkAfterColumns);
                }
                columns.add(column);
            }
        }

        for (SqlColumnMapping tableColumn : table.columns()) {
            String column = tableColumn.getName();
            if (escape) {
                column = quote(column);
            }
            if (StringUtils.isNotEmpty(tableColumn.getDefinition())) {
                column += " " + tableColumn.getDefinition();
            } else if (tableColumn.getDbType() == SqlDbType.JSON_OBJECT) {
                column += " JSON(OBJECT)";
            } else {
                column += " " + tableColumn.getSqlType(dialect);
                if (tableColumn.isRequired()) {
                    column += " NOT NULL";
                }
            }
            if (tableColumn.isAutoGenerated()) {
                column = addGeneratedStatementToColumn(tableColumn.getGeneratedValueType(), tableColumn.getDataType(), column, false);
            }
            columns.add(column);
        }

        String tableName = getObjectName(schema, table.name(), escape, true);
        StringBuilder builder = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
        builder.append(String.join(",", columns));
        if (generatePkAfterColumns) {
            builder.append(", PRIMARY KEY(").append(String.join(",", primaryColumnsName)).append(')');
        }
        if (dialect == Dialect.ORACLE) {
            builder.append(")");
        } else {
            builder.append(");");
        }
        addToCollectionIfNotContains(createStatements, builder.toString());
        createSequenceStatements(table, escape, createStatements);
        createAuxiliaryStatements(table, createStatements);
        createIndexStatements(table, tableName, escape, createStatements);
    }

    private void createAuxiliaryStatements(SqlTableMapping table, List<String> createStatements) {
        List<String> auxiliaryStatements = table.auxiliaryStatements();
        if (CollectionUtils.isEmpty(auxiliaryStatements)) {
            return;
        }
        for (String auxiliaryStatement : auxiliaryStatements) {
            addToCollectionIfNotContains(createStatements, dialect == Dialect.ORACLE ? auxiliaryStatement : auxiliaryStatement + ';');
        }
    }

    private void createSequenceStatements(SqlTableMapping table, boolean escape, List<String> createStatements) {
        List<SqlSequenceMapping> sequences = table.sequences();
        if (CollectionUtils.isEmpty(sequences)) {
            return;
        }
        for (SqlSequenceMapping sequence : sequences) {
            if (sequence.definition() != null) {
                addToCollectionIfNotContains(createStatements, sequence.definition());
            } else {
                GeneratedValue.Type idGeneratorType = sequence.generatedValueType().orElseGet(() -> defaultSelectAutoStrategy(sequence.dataType(), dialect));
                boolean isSequence = idGeneratorType == SEQUENCE;
                if (isSequence) {
                    addToCollectionIfNotContains(createStatements, createSequenceStmt(table.schema(), table.name(), sequence.definedName(), escape));
                }
            }
        }
    }

    private void createIndexStatements(SqlTableMapping table, String escapedTableName, boolean escape, List<String> createStatements) {
        List<SqlIndexMapping> indexes = table.indexes();
        if (CollectionUtils.isEmpty(indexes)) {
            return;
        }
        List<String> indexNames = new ArrayList<>(indexes.size());
        for (SqlIndexMapping indexMapping : indexes) {
            String indexName = createIndexName(table, indexMapping, escape);
            if (indexNames.contains(indexName)) {
                continue;
            }
            indexNames.add(indexName);
            addToCollectionIfNotContains(createStatements, createIndexStatement(table, indexMapping, indexName, escapedTableName, escape));
        }
    }

    private String createIndexName(SqlTableMapping tableMapping, SqlIndexMapping indexMapping, boolean escape) {
        // Create index name without escaped table name and then escape if needed
        String columnNames = String.join(", ", indexMapping.columns());
        String indexName = StringUtils.isNotEmpty(indexMapping.name()) ? indexMapping.name() :
            String.format("idx_%s%s", prepareNames(tableMapping.name()),
                makeTransformedColumnList(columnNames));
        if (escape) {
            indexName = quote(indexName);
        }
        return indexName;
    }

    @SuppressWarnings("java:S3776")
    private String createIndexStatement(SqlTableMapping tableMapping, SqlIndexMapping indexMapping, String indexName, String escapedTableName, boolean escape) {
        String columnNames = String.join(", ", indexMapping.columns());
        SqlIndexDefinitionProvider sqlIndexDefinitionProvider = indexMapping.sqlIndexDefinitionProvider();
        if (sqlIndexDefinitionProvider != null) {
            return sqlIndexDefinitionProvider.getIndexDefinition(
                indexName,
                escapedTableName,
                indexMapping.columns(),
                escape,
                this::quote,
                indexMapping,
                dialect
            );
        }

        StringBuilder indexBuilder = new StringBuilder();
        indexBuilder.append("CREATE ");
        if (indexMapping.unique()) {
            indexBuilder.append("UNIQUE ");
        } else if (indexMapping.spatial() && (dialect == Dialect.MYSQL || dialect == Dialect.SQL_SERVER || dialect == Dialect.H2)) {
            indexBuilder.append("SPATIAL ");
        }
        indexBuilder.append("INDEX ");
        String indexColumnNames = escape ? String.join(", ", Arrays.stream(indexMapping.columns()).map(this::quote).toList()) : columnNames;
        indexBuilder.append(indexName).append(" ON ").append(escapedTableName);
        if (indexMapping.spatial() && dialect == Dialect.POSTGRES) {
            indexBuilder.append(" USING GIST");
        }
        indexBuilder.append(" (").append(indexColumnNames);
        if (dialect == Dialect.ORACLE) {
            indexBuilder.append(")");
            if (indexMapping.spatial()) {
                indexBuilder.append(" INDEXTYPE IS MDSYS.SPATIAL_INDEX");
            }
        } else if (dialect == Dialect.SQL_SERVER) {
            indexBuilder.append(")");
            if (indexMapping.spatial()) {
                // sqlserver geospatial columns can be geometry or geography type
                // when geometry column type is used, the index must have BOUNDING_BOX
                Optional<SqlColumnMapping> optSqlColumnMapping = tableMapping.columns()
                    .stream()
                    .filter(column -> column.getName().equals(indexMapping.columns()[0]))
                    .findFirst();
                if (optSqlColumnMapping.isPresent()) {
                    String definition = optSqlColumnMapping.get().getDefinition();
                    if (definition != null && definition.toLowerCase().contains("geometry")) {
                        Integer srid = indexMapping.srid();
                        if (Objects.equals(SqlSchemaUtils.SRID_WGS_84, srid) || Objects.equals(SqlSchemaUtils.SRID_ETRS_89, srid)) {
                            indexBuilder.append(" USING GEOMETRY_GRID WITH (BOUNDING_BOX = (-180, -90, 180,  90))");
                        } else if (Objects.equals(SqlSchemaUtils.SRID_WEB_MERCATOR, srid)) {
                            indexBuilder.append(" USING GEOMETRY_GRID WITH (BOUNDING_BOX = (-20037508.3427892, -20037508.3427892, 20037508.3427892,  20037508.3427892))");
                        }
                    }
                }
            }
            indexBuilder.append(";");
        } else {
            indexBuilder.append(");");
        }
        return indexBuilder.toString();
    }

    private String createSequenceStmt(@Nullable String schema, String tableName, @Nullable String definedName, boolean escape) {
        final String sequenceName = getObjectName(schema, StringUtils.isNotEmpty(definedName) ? Objects.requireNonNull(definedName) : tableName + SqlQueryBuilderUtils.SEQ_SUFFIX, escape, true);
        final boolean isSqlServer = dialect == Dialect.SQL_SERVER;
        String createSequenceStmt = "CREATE SEQUENCE " + sequenceName;
        if (isSqlServer) {
            createSequenceStmt += " AS BIGINT";
        }

        createSequenceStmt += " MINVALUE 1 START WITH 1";
        if (dialect == Dialect.ORACLE) {
            createSequenceStmt += " CACHE 100 NOCYCLE";
        } else {
            if (isSqlServer) {
                createSequenceStmt += " INCREMENT BY 1";
            }
        }
        return createSequenceStmt;
    }

    private String makeTransformedColumnList(String columnList) {
        return Arrays.stream(prepareNames(columnList).split(","))
            .map(col -> "_" + col)
            .collect(Collectors.joining());
    }

    private String prepareNames(String columnList) {
        return columnList.chars()
            .mapToObj(c -> String.valueOf((char) c))
            .filter(x -> !x.equals(" "))
            .filter(x -> !x.equals("\""))
            .map(String::toLowerCase)
            .collect(Collectors.joining());
    }

    @Override
    protected String getTableAsKeyword() {
        return BLANK_SPACE;
    }

    @SuppressWarnings("java:S3776")
    private String addGeneratedStatementToColumn(GeneratedValue.Type type, DataType dataType, String column, boolean isPk) {
        if (type == AUTO) {
            if (dataType == DataType.UUID) {
                type = UUID;
            } else if (dialect == Dialect.ORACLE) {
                type = SEQUENCE;
            } else {
                type = IDENTITY;
            }
        }
        boolean addPkBefore = dialect != Dialect.H2 && dialect != Dialect.ORACLE;
        if (isPk && addPkBefore) {
            column += " PRIMARY KEY";
        }
        switch (dialect) {
            case POSTGRES:
                if (type == SEQUENCE) {
                    column += " NOT NULL";
                } else if (type == IDENTITY) {
                    if (isPk) {
                        column += " GENERATED ALWAYS AS IDENTITY";
                    } else {
                        column += " NOT NULL";
                    }
                } else if (type == UUID) {
                    column += " NOT NULL DEFAULT uuid_generate_v4()";
                }
                break;
            case H2:
                if (type == SEQUENCE) {
                    column += " NOT NULL";
                } else if (type == IDENTITY) {
                    if (isPk) {
                        column += " GENERATED ALWAYS AS IDENTITY";
                    } else {
                        column += " NOT NULL";
                    }
                } else if (type == UUID) {
                    column += " NOT NULL DEFAULT random_uuid()";
                }
                break;
            case SQL_SERVER:
                if (type == UUID) {
                    column += " NOT NULL DEFAULT newid()";
                } else if (type == SEQUENCE) {
                    if (isPk) {
                        column += " NOT NULL";
                    }
                } else {
                    column += " IDENTITY(1,1) NOT NULL";
                }
                break;
            case ORACLE:
                // for Oracle, we use sequences so just add NOT NULL
                // then alter the table for sequences
                if (type == UUID) {
                    column += " NOT NULL DEFAULT SYS_GUID()";
                } else if (type == IDENTITY) {
                    if (isPk) {
                        column += " GENERATED BY DEFAULT ON NULL AS IDENTITY (MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE)";
                    } else {
                        column += " NOT NULL";
                    }
                } else {
                    column += " NOT NULL";
                }
                break;
            case MYSQL:
                if (type == UUID) {
                    column += " NOT NULL";
                } else if (dataType.isNumeric()) {
                    column += " AUTO_INCREMENT";
                }
                break;
            default:
                if (type == UUID) {
                    column += " NOT NULL DEFAULT random_uuid()";
                }
        }
        if (isPk && !addPkBefore) {
            column += " PRIMARY KEY";
        }
        return column;
    }

    private List<String> resolveJoinTableAssociatedColumns(AnnotationMetadata annotationMetadata, boolean associationOwner, PersistentEntity entity, NamingStrategy namingStrategy) {
        List<String> joinColumns = SqlQueryBuilderUtils.getJoinedColumns(annotationMetadata, associationOwner, REFERENCED_COLUMN_NAME);
        if (!joinColumns.isEmpty()) {
            return joinColumns;
        }
        if (!entity.hasIdentity()) {
            throw new MappingException("Cannot have a foreign key association without an ID on entity: " + entity.getName());
        }
        PersistentProperty identity = entity.getIdentity();
        List<String> columns = new ArrayList<>();
        PersistentEntityUtils.traversePersistentProperties(identity, (associations, property) -> {
            String columnName = getMappedName(namingStrategy, associations, property);
            columns.add(columnName);
        });
        return columns;
    }

    @Override
    protected SqlSelectionVisitor createSelectionVisitor(AnnotationMetadata annotationMetadata, QueryState queryState, boolean distinct) {
        return new SqlSelectionVisitor(queryState, annotationMetadata, distinct);
    }

    @Override
    protected ReturningSelectionVisitor createReturningSelectionVisitor(AnnotationMetadata annotationMetadata, QueryState queryState, boolean distinct) {
        return new DefaultReturningSelectionVisitor(queryState, annotationMetadata, distinct);
    }

    @Override
    public String resolveJoinType(Join.Type jt) {
        if (!this.dialect.supportsJoinType(jt)) {
            throw new IllegalArgumentException("Unsupported join type [" + jt + "] by dialect [" + this.dialect + "]");
        }
        return switch (jt) {
            case LEFT, LEFT_FETCH -> " LEFT JOIN ";
            case RIGHT, RIGHT_FETCH -> " RIGHT JOIN ";
            case OUTER, OUTER_FETCH -> " FULL OUTER JOIN ";
            default -> " INNER JOIN ";
        };
    }

    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition definition) {
        if (definition.returning() && !getDialect().supportsInsertReturning()) {
            throw new IllegalStateException("Dialect: " + getDialect() + " doesn't support INSERT ... RETURNING clause");
        }
        PersistentEntity entity = definition.persistentEntity();

        boolean escape = shouldEscape(entity);
        final String unescapedTableName = getUnescapedTableName(entity);
        final String unescapedSchema = SqlQueryBuilderUtils.getSchemaName(entity);

        String builder;
        List<String> resultColumns = new ArrayList<>();
        List<String> unescapedColumns = new ArrayList<>();
        List<DataType> resultColumnTypes = new ArrayList<>();
        List<QueryParameterBinding> parameterBindings = new ArrayList<>();

        if (isJsonEntity(repositoryMetadata, entity)) {
            AnnotationValue<EntityRepresentation> entityRepresentationAnnotationValue = entity.getAnnotationMetadata().getAnnotation(EntityRepresentation.class);
            if (entityRepresentationAnnotationValue == null) {
                throw new MappingException("Cannot find entity representation annotation for entity: " + entity.getName());
            }
            String columnName = entityRepresentationAnnotationValue.getRequiredValue("column", String.class);
            int key = 1;
            builder = INSERT_INTO + getTableName(entity) + " VALUES (" + formatParameter(key) + ")";
            for (PersistentProperty identity : entity.getIdentityProperties()) {
                if (identity.isGenerated()) {
                    String identityName = identity.getAnnotationMetadata().stringValue(SERDE_CONFIG_ANNOTATION, "property")
                        .orElse(identity.getAnnotationMetadata().stringValue(JSON_PROPERTY_ANNOTATION)
                            .orElse(identity.getName()));
                    resultColumns.add(identityName);
                    resultColumnTypes.add(identity.getDataType());
                    unescapedColumns.add(identityName);
                    builder = "BEGIN " + builder + " RETURNING JSON_VALUE(" + columnName + ",'$." + identityName + "') INTO " + formatParameter(key + 1) + "; END;";
                }
                parameterBindings.add(new QueryParameterBinding() {

                    @Override
                    public String getName() {
                        return String.valueOf(key);
                    }

                    @Override
                    public String getKey() {
                        return String.valueOf(key);
                    }

                    @Override
                    public DataType getDataType() {
                        return DataType.JSON;
                    }

                    @Override
                    public JsonDataType getJsonDataType() {
                        return JsonDataType.DEFAULT;
                    }

                });
            }
        } else {

            NamingStrategy namingStrategy = getNamingStrategy(entity);

            Collection<? extends PersistentProperty> persistentProperties = entity.getPersistentProperties();
            List<String> columns = new ArrayList<>();
            List<String> values = new ArrayList<>();

            for (PersistentProperty prop : persistentProperties) {
                PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), prop, (associations, property) -> {
                    boolean generated = SqlQueryBuilderUtils.isGeneratedProperty(property, associations);
                    if (generated) {
                        String columnName = getMappedName(namingStrategy, associations, property);
                        unescapedColumns.add(columnName);
                        if (escape) {
                            columnName = quote(columnName);
                        }
                        resultColumns.add(columnName);
                        resultColumnTypes.add(property.getDataType());
                        return;
                    }

                    addWriteExpression(values, property);

                    String key = String.valueOf(values.size());
                    String[] path = asStringPath(associations, property);
                    parameterBindings.add(new QueryParameterBinding() {
                        @Override
                        public String getName() {
                            return key;
                        }

                        @Override
                        public String getKey() {
                            return key;
                        }

                        @Override
                        public DataType getDataType() {
                            return property.getDataType();
                        }

                        @Override
                        public JsonDataType getJsonDataType() {
                            return property.getJsonDataType();
                        }

                        @Override
                        public String[] getPropertyPath() {
                            return path;
                        }
                    });

                    String columnName = getMappedName(namingStrategy, associations, property);
                    unescapedColumns.add(columnName);
                    if (escape) {
                        columnName = quote(columnName);
                    }
                    columns.add(columnName);
                    resultColumns.add(columnName);
                    resultColumnTypes.add(property.getDataType());
                });
            }
            if (entity.hasVersion()) {
                PersistentProperty version = entity.getVersion();
                if (!version.isGenerated()) {
                    addWriteExpression(values, version);

                    String key = String.valueOf(values.size());
                    parameterBindings.add(new QueryParameterBinding() {

                        @Override
                        public String getName() {
                            return key;
                        }

                        @Override
                        public String getKey() {
                            return key;
                        }

                        @Override
                        public DataType getDataType() {
                            return version.getDataType();
                        }

                        @Override
                        public String[] getPropertyPath() {
                            return new String[]{version.getName()};
                        }
                    });

                    String columnName = getMappedName(namingStrategy, Collections.emptyList(), version);
                    unescapedColumns.add(columnName);
                    if (escape) {
                        columnName = quote(columnName);
                    }
                    columns.add(columnName);
                    resultColumns.add(columnName);
                    resultColumnTypes.add(version.getDataType());
                }
            }

            for (PersistentProperty identity : entity.getIdentityProperties()) {
                // Property skipped
                PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property) -> {
                    String unescapedColumnName = getMappedName(namingStrategy, associations, property);
                    String columnName = unescapedColumnName;
                    if (escape) {
                        columnName = quote(columnName);
                    }

                    boolean isSequence = false;
                    if (SqlQueryBuilderUtils.isNotForeign(associations)) {

                        unescapedColumns.add(unescapedColumnName);
                        resultColumns.add(columnName);
                        resultColumnTypes.add(property.getDataType());

                        Optional<AnnotationValue<GeneratedValue>> generated = property.findAnnotation(GeneratedValue.class);
                        if (generated.isPresent()) {
                            GeneratedValue.Type idGeneratorType = generated
                                .flatMap(av -> av.enumValue(GeneratedValue.Type.class))
                                .orElseGet(() -> selectAutoStrategy(property));
                            if (idGeneratorType == SEQUENCE) {
                                isSequence = true;
                            } else if (dialect != Dialect.MYSQL || property.getDataType() != DataType.UUID) {
                                // Property skipped
                                return;
                            }
                        }
                    }

                    if (isSequence) {
                        values.add(getSequenceStatement(unescapedSchema, unescapedTableName, property));
                    } else {
                        addWriteExpression(values, property);

                        String key = String.valueOf(values.size());
                        String[] path = asStringPath(associations, property);
                        parameterBindings.add(new QueryParameterBinding() {

                            @Override
                            public String getName() {
                                return key;
                            }

                            @Override
                            public String getKey() {
                                return key;
                            }

                            @Override
                            public DataType getDataType() {
                                return property.getDataType();
                            }

                            @Override
                            public JsonDataType getJsonDataType() {
                                return property.getJsonDataType();
                            }

                            @Override
                            public String[] getPropertyPath() {
                                return path;
                            }
                        });

                    }

                    columns.add(columnName);
                });
            }

            builder = INSERT_INTO + getTableName(entity) +
                " (" + String.join(",", columns) + CLOSE_BRACKET + " " +
                "VALUES (" + String.join(String.valueOf(COMMA), values) + CLOSE_BRACKET;

            if (definition.returning()) {
                if (dialect == Dialect.ORACLE) {
                    // For Oracle use RETURNING all result columns INTO placeholders with CallableStatement.
                    if (resultColumns.isEmpty()) {
                        throw new IllegalStateException("INSERT ... RETURNING requires at least one column to return for entity: " + entity.getName());
                    }
                    List<String> outPlaceholders = new ArrayList<>(resultColumns.size());
                    for (int i = 0; i < resultColumns.size(); i++) {
                        outPlaceholders.add(formatParameter(values.size() + 1 + i).name());
                    }
                    builder = "BEGIN " + builder + " RETURNING " + String.join(",", resultColumns) + " INTO " + String.join(",", outPlaceholders) + "; END;";
                } else {
                    // Postgres and others using a result set for RETURNING
                    builder += RETURNING + String.join(",", resultColumns);
                }
            }
        }
        if (definition.returning() && dialect == Dialect.ORACLE) {
            // Attach OUT parameter bindings metadata (columns listed in RETURNING ...)
            List<QueryOutParameterBinding> outBindings = new ArrayList<>();
            for (int i = 0; i < unescapedColumns.size(); i++) {
                final String col = unescapedColumns.get(i);
                final DataType dt = i < resultColumnTypes.size() ? resultColumnTypes.get(i) : DataType.STRING;
                outBindings.add(new QueryOutParameterBinding() {
                    @Override
                    public String getName() {
                        return col;
                    }

                    @Override
                    public DataType getDataType() {
                        return dt;
                    }
                });
            }
            return QueryResult.of(builder, List.of(), parameterBindings, outBindings, Map.of());
        }
        return QueryResult.of(builder,
            Collections.emptyList(),
            parameterBindings,
            Collections.emptyMap());
    }

    private String[] asStringPath(List<Association> associations, PersistentProperty property) {
        if (associations.isEmpty()) {
            return new String[]{property.getName()};
        }
        List<String> path = new ArrayList<>(associations.size() + 1);
        for (Association association : associations) {
            path.add(association.getName());
        }
        path.add(property.getName());
        return path.toArray(new String[0]);
    }

    private String getSequenceStatement(String unescapedSchemaName, String unescapedTableName, PersistentProperty property) {
        final String sequenceName = resolveSequenceName(property, unescapedTableName);
        return switch (dialect) {
            case ORACLE -> (StringUtils.isEmpty(unescapedSchemaName) ? "" : quote(unescapedSchemaName, true) + DOT) + quote(sequenceName, true) + ".nextval";
            case POSTGRES -> "nextval('" + (StringUtils.isEmpty(unescapedSchemaName) ? "" : unescapedSchemaName + DOT) + sequenceName + "')";
            case SQL_SERVER -> "NEXT VALUE FOR " + (StringUtils.isEmpty(unescapedSchemaName) ? "" : quote(unescapedSchemaName, true) + DOT) + quote(sequenceName, true);
            default -> throw new IllegalStateException("Cannot generate a sequence for dialect: " + dialect);
        };
    }

    private String resolveSequenceName(PersistentProperty identity, String unescapedTableName) {
        return identity.getAnnotationMetadata().stringValue(GeneratedValue.class, "ref")
            .map(n -> {
                if (StringUtils.isEmpty(n)) {
                    return unescapedTableName + SqlQueryBuilderUtils.SEQ_SUFFIX;
                } else {
                    return n;
                }
            })
            .orElseGet(() -> unescapedTableName + SqlQueryBuilderUtils.SEQ_SUFFIX);
    }

    @Override
    protected String getAliasName(PersistentEntity entity) {
        return entity.getAliasName();
    }

    @Override
    public String getTableName(PersistentEntity entity) {
        boolean escape = shouldEscape(entity);
        String tableName = entity.getPersistedName();
        String schema = SqlQueryBuilderUtils.getSchemaName(entity);
        return getObjectName(schema, tableName, escape, true);
    }

    private String getObjectName(@Nullable String schema, String objectName, boolean escape, boolean objectSupportsDynamicValues) {
        if (StringUtils.isNotEmpty(schema)) {
            Objects.requireNonNull(schema);
            if (escape) {
                return quote(schema, true) + '.' + quote(objectName, objectSupportsDynamicValues);
            } else {
                return schema + '.' + objectName;
            }
        } else {
            return escape ? quote(objectName, objectSupportsDynamicValues) : objectName;
        }
    }

    private boolean addWriteExpression(List<String> values, PersistentProperty property) {
        DataType dt = property.getDataType();
        String transformer = getDataTransformerWriteValue(null, property).orElse(null);
        if (transformer != null) {
            return values.add(transformer);
        }
        String param = formatParameter(values.size() + 1).name();
        if (dt == DataType.JSON) {
            switch (dialect) {
                case POSTGRES -> values.add("to_json(" + param + "::json)");
                case H2 -> values.add(param + " FORMAT JSON");
                case MYSQL -> values.add("CONVERT(" + param + " USING UTF8MB4)");
                default -> values.add(param);
            }
            return true;
        }
        if (isJsonOrWktGeometry(property)) {
            switch (dialect) {
                case ORACLE -> values.add(getOracleGeometryExpression(param, property));
                case MYSQL -> values.add(getMysqlGeometryExpression(param, property));
                case SQL_SERVER -> values.add(getSqlServerGeometryExpression(param, property));
                case POSTGRES, H2 -> values.add(getPostgresGeometryExpression(param, property));
                default -> values.add(param);
            }
            return true;
        }
        return values.add(param);
    }

    @Override
    protected void appendUpdateSetParameter(StringBuilder sb, @Nullable String alias, PersistentProperty prop, Runnable appendParameter) {
        String transformed = getDataTransformerWriteValue(alias, prop).orElse(null);
        if (transformed != null) {
            appendTransformed(sb, transformed, appendParameter);
            return;
        }
        if (prop.getDataType() == DataType.JSON) {
            switch (dialect) {
                case H2:
                    appendParameter.run();
                    sb.append(" FORMAT JSON");
                    break;
                case MYSQL:
                    sb.append("CONVERT(");
                    appendParameter.run();
                    sb.append(" USING UTF8MB4)");
                    break;
                case POSTGRES:
                    sb.append("to_json(");
                    appendParameter.run();
                    sb.append("::json)");
                    break;
                default:
                    super.appendUpdateSetParameter(sb, alias, prop, appendParameter);
            }
        } else if (isJsonOrWktGeometry(prop)) {
            switch (dialect) {
                case ORACLE:
                    appendOracleGeometryExpression(sb, prop, appendParameter);
                    break;
                case MYSQL:
                    appendMysqlGeometryExpression(sb, prop, appendParameter);
                    break;
                case SQL_SERVER:
                    appendSqlServerGeometryExpression(sb, prop, appendParameter);
                    break;
                case POSTGRES, H2:
                    appendPostgresGeometryExpression(sb, prop, appendParameter);
                    break;
                default:
                    super.appendUpdateSetParameter(sb, alias, prop, appendParameter);
            }
        } else {
            super.appendUpdateSetParameter(sb, alias, prop, appendParameter);
        }
    }

    private String getOracleGeometryExpression(String parameter, PersistentProperty property) {
        StringBuilder sb = new StringBuilder();
        appendOracleGeometryExpression(sb, property, () -> sb.append(parameter));
        return sb.toString();
    }

    private void appendOracleGeometryExpression(StringBuilder sb, PersistentProperty property, Runnable appendParameter) {
        AnnotationMetadata annotationMetadata = property.getAnnotationMetadata();
        OptionalInt optSrid = annotationMetadata.intValue(Srid.class);
        String converter = annotationMetadata.stringValue(MappedProperty.class, "converter").orElse(null);
        boolean isWkt = GeometryWktConverter.class.getName().equals(converter);
        if (isWkt) {
            sb.append("SDO_UTIL.FROM_WKTGEOMETRY(TO_CHAR(");
        } else {
            sb.append("SDO_UTIL.FROM_GEOJSON(");
        }
        appendParameter.run();
        if (isWkt) {
            sb.append(")");
        }
        if (optSrid.isPresent()) {
            sb.append(", ");
            if (isWkt) {
                sb.append(optSrid.getAsInt());
            } else {
                sb.append("NULL, ").append(optSrid.getAsInt());
            }
        }
        sb.append(")");
    }

    private String getMysqlGeometryExpression(String parameter, PersistentProperty property) {
        StringBuilder sb = new StringBuilder();
        appendMysqlGeometryExpression(sb, property, () -> sb.append(parameter));
        return sb.toString();
    }

    private void appendMysqlGeometryExpression(StringBuilder sb, PersistentProperty property, Runnable appendParameter) {
        AnnotationMetadata annotationMetadata = property.getAnnotationMetadata();
        OptionalInt optSrid = annotationMetadata.intValue(Srid.class);
        String converter = annotationMetadata.stringValue(MappedProperty.class, "converter").orElse(null);
        boolean isWkt = GeometryWktConverter.class.getName().equals(converter);
        if (isWkt) {
            sb.append("ST_GeomFromText(");
        } else {
            sb.append("ST_GeomFromGeoJSON(");
        }
        appendParameter.run();
        if (optSrid.isPresent()) {
            sb.append(", ");
            if (isWkt) {
                sb.append(optSrid.getAsInt());
            } else {
                sb.append("1, ").append(optSrid.getAsInt());
            }
        }
        sb.append(")");
    }

    private String getSqlServerGeometryExpression(String parameter, PersistentProperty property) {
        StringBuilder sb = new StringBuilder();
        appendSqlServerGeometryExpression(sb, property, () -> sb.append(parameter));
        return sb.toString();
    }

    private void appendSqlServerGeometryExpression(StringBuilder sb, PersistentProperty property, Runnable appendParameter) {
        // since sqlserver doesn't have built-in functions for conversion between
        // json and internal geospatial data type, use always Well-Known Text (WKT) functions
        AnnotationMetadata annotationMetadata = property.getAnnotationMetadata();
        Optional<String> optDefinition = annotationMetadata.stringValue(MappedProperty.class, "definition");
        OptionalInt optSrid = annotationMetadata.intValue(Srid.class);

        String geoDataType;
        int defaultSrid;
        if (optDefinition.isPresent() && optDefinition.get().toLowerCase().contains("geography")) {
            geoDataType = "geography";
            defaultSrid = 4326;
        } else {
            geoDataType = "geometry";
            defaultSrid = 3857;
        }

        sb.append(geoDataType).append("::STGeomFromText(");
        appendParameter.run();
        sb.append(", ").append(optSrid.orElse(defaultSrid)).append(")");
    }

    private String getPostgresGeometryExpression(String parameter, PersistentProperty property) {
        StringBuilder sb = new StringBuilder();
        appendPostgresGeometryExpression(sb, property, () -> sb.append(parameter));
        return sb.toString();
    }

    private void appendPostgresGeometryExpression(StringBuilder sb, PersistentProperty property, Runnable appendParameter) {
        AnnotationMetadata annotationMetadata = property.getAnnotationMetadata();
        OptionalInt optSrid = annotationMetadata.intValue(Srid.class);
        String converter = annotationMetadata.stringValue(MappedProperty.class, "converter").orElse(null);
        boolean isWkt = GeometryWktConverter.class.getName().equals(converter);
        if (isWkt) {
            sb.append("ST_GeomFromText(");
            appendParameter.run();
            if (optSrid.isPresent()) {
                sb.append(", ").append(optSrid.getAsInt());
            }
            sb.append(")");
        } else {
            if (optSrid.isPresent()) {
                sb.append("ST_SetSRID(");
            }
            sb.append("ST_GeomFromGeoJSON(");
            appendParameter.run();
            sb.append(')');
            if (optSrid.isPresent()) {
                sb.append(", ").append(optSrid.getAsInt()).append(')');
            }
        }
        Optional<String> optDefinition = annotationMetadata.stringValue(MappedProperty.class, "definition");
        if (optDefinition.isPresent() && optDefinition.get().toLowerCase().contains("geography")) {
            // convert result of ST_GeomFromText and ST_GeomFromGeoJSON to geography
            sb.append("::geography");
        }
    }

    @Override
    protected void buildJoin(@Nullable String joinType,
                             StringBuilder query,
                             QueryState queryState,
                             PersistentAssociationPath joinAssociation,
                             PersistentEntity associationOwner,
                             String currentJoinAlias,
                             String lastJoinAlias) {
        if (joinType == null) {
            joinType = JoinType.INNER.name();
        }
        Association association = joinAssociation.getAssociation();
        List<Association> joinAssociationsPath = joinAssociation.getAssociations();
        PersistentEntity associatedEntity = association.getAssociatedEntity();
        final boolean escape = shouldEscape(associationOwner);
        String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
        AnnotationValue<JoinColumns> joinColumnsAnnotationValue = association.getAnnotationMetadata().getAnnotation(JoinColumns.class);
        List<AnnotationValue<JoinColumn>> joinColumnValues = joinColumnsAnnotationValue == null ? null : joinColumnsAnnotationValue.getAnnotations(VALUE_MEMBER);
        boolean isManyToMany = association.getKind() == Relation.Kind.MANY_TO_MANY;

        if (isManyToMany || association.getKind() == Relation.Kind.MANY_TO_MANY || (association.isForeignKey() && StringUtils.isEmpty(mappedBy) && CollectionUtils.isEmpty(joinColumnValues))) {
            if (!associatedEntity.hasIdentity()) {
                throw new IllegalArgumentException("Associated entity [" + associatedEntity.getName() + "] defines no ID. Cannot join.");
            }
            if (!associationOwner.hasIdentity()) {
                throw new MappingException("Cannot join on entity [" + associationOwner.getName() + "] that has no declared ID");
            }
            Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
            Association owningAssociation = inverseSide.orElse(association);
            boolean isAssociationOwner = association.getInverseSide().isEmpty();
            NamingStrategy namingStrategy = associationOwner.getNamingStrategy();
            AnnotationMetadata annotationMetadata = owningAssociation.getAnnotationMetadata();

            List<String> ownerJoinColumns = resolveJoinTableAssociatedColumns(annotationMetadata, isAssociationOwner, associationOwner, namingStrategy);
            List<String> ownerJoinTableColumns = SqlQueryBuilderUtils.resolveJoinTableJoinColumns(annotationMetadata,
                isAssociationOwner, associationOwner, namingStrategy);
            List<String> associationJoinColumns = resolveJoinTableAssociatedColumns(annotationMetadata, !isAssociationOwner, associatedEntity, namingStrategy);
            List<String> associationJoinTableColumns = SqlQueryBuilderUtils.resolveJoinTableJoinColumns(annotationMetadata, !isAssociationOwner, associatedEntity, namingStrategy);
            if (escape) {
                ownerJoinColumns = ownerJoinColumns.stream().map(this::quote).toList();
                ownerJoinTableColumns = ownerJoinTableColumns.stream().map(this::quote).toList();
                associationJoinColumns = associationJoinColumns.stream().map(this::quote).toList();
                associationJoinTableColumns = associationJoinTableColumns.stream().map(this::quote).toList();
            }

            String joinTableSchema = annotationMetadata
                .stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, SqlMembers.SCHEMA)
                .orElse(SqlQueryBuilderUtils.getSchemaName(associationOwner));
            if (StringUtils.isNotEmpty(joinTableSchema) && escape) {
                joinTableSchema = quote(joinTableSchema, true);
            }
            String joinTableName = annotationMetadata
                .stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, "name")
                .orElseGet(() -> getMappedName(namingStrategy, association));
            String joinTableAlias = annotationMetadata
                .stringValue(SqlQueryBuilderUtils.ANN_JOIN_TABLE, "alias")
                .orElseGet(() -> currentJoinAlias + joinTableName + "_");
            String finalTableName = escape ? quote(joinTableName, true) : joinTableName;
            if (StringUtils.isNotEmpty(joinTableSchema)) {
                finalTableName = joinTableSchema + DOT + finalTableName;
            }
            if (queryState.baseQueryDefinition().persistentEntity().getAnnotationMetadata().hasAnnotation(JsonSubView.class)) {
                query.append(WHERE_CLAUSE);
                if (Objects.equals(joinType, JoinType.LEFT.name()) || isManyToMany) {
                    join(query,
                        queryState.baseQueryDefinition(),
                        joinType,
                        finalTableName,
                        isManyToMany ? currentJoinAlias : joinTableName,
                        lastJoinAlias,
                        ownerJoinColumns,
                        ownerJoinTableColumns);
                } else {
                    join(query,
                        queryState.baseQueryDefinition(),
                        joinType,
                        getTableName(associatedEntity),
                        currentJoinAlias,
                        joinTableName,
                        associationJoinTableColumns,
                        associationJoinColumns);
                }
                return;
            } else {
                join(query,
                    queryState.baseQueryDefinition(),
                    joinType,
                    finalTableName,
                    joinTableAlias,
                    lastJoinAlias,
                    ownerJoinColumns,
                    ownerJoinTableColumns);
                query.append(SPACE);
                join(query,
                    queryState.baseQueryDefinition(),
                    joinType,
                    getTableName(associatedEntity),
                    currentJoinAlias,
                    joinTableAlias,
                    associationJoinTableColumns,
                    associationJoinColumns);
            }
        } else {
            if (StringUtils.isNotEmpty(mappedBy)) {
                Objects.requireNonNull(mappedBy);
                if (!associationOwner.hasIdentity()) {
                    throw new IllegalArgumentException("Associated entity [" + associationOwner + "] defines no ID. Cannot join.");
                }
                PersistentProperty ownerIdentity = associationOwner.getIdentity();
                PersistentPropertyPath mappedByPropertyPath = associatedEntity.getPropertyPath(mappedBy);
                if (mappedByPropertyPath == null) {
                    throw new MappingException("Foreign key association with mappedBy references a property that doesn't exist [" + mappedBy + "] of entity: " + associatedEntity.getName());
                }
                join(query,
                    joinType,
                    queryState,
                    associatedEntity,
                    associationOwner,
                    lastJoinAlias,
                    currentJoinAlias,
                    new PersistentPropertyPath(joinAssociationsPath, ownerIdentity),
                    mappedByPropertyPath);
            } else {
                if (!associatedEntity.hasIdentity()) {
                    throw new IllegalArgumentException("Associated entity [" + associatedEntity.getName() + "] defines no ID. Cannot join.");
                }
                PersistentProperty associatedProperty = associatedEntity.getIdentity();
                join(query,
                    joinType,
                    queryState,
                    associatedEntity,
                    associationOwner,
                    lastJoinAlias,
                    currentJoinAlias,
                    joinAssociation,
                    new PersistentPropertyPath(List.of(), associatedProperty));
            }
        }

        String additionalWhere = resolveWhereForAnnotationMetadata(currentJoinAlias, associatedEntity.getAnnotationMetadata());
        if (StringUtils.isNotEmpty(additionalWhere)) {
            query.append(LOGICAL_AND).append(additionalWhere);
        }
    }

    private void join(StringBuilder sb,
                      String joinType,
                      QueryState queryState,
                      PersistentEntity associatedEntity,
                      PersistentEntity associationOwner,
                      String leftTableAlias,
                      String rightTableAlias,
                      PersistentPropertyPath leftPropertyPath,
                      PersistentPropertyPath rightPropertyPath) {

        final boolean escape = shouldEscape(associationOwner);
        List<String> onLeftColumns = new ArrayList<>();
        List<String> onRightColumns = new ArrayList<>();

        PersistentProperty leftProperty = leftPropertyPath.getProperty();
        PersistentProperty rightProperty = rightPropertyPath.getProperty();

        Association association = null;
        if (leftProperty instanceof Association associationLeft) {
            association = associationLeft;
        } else if (rightProperty instanceof Association associationRight) {
            association = associationRight;
        }
        if (association != null) {
            Optional<Association> inverse = association.getInverseSide().map(Function.identity());
            Association owner = inverse.orElse(association);
            boolean isOwner = leftProperty == owner;
            AnnotationValue<Annotation> joinColumnsHolder = owner.getAnnotationMetadata().getAnnotation(SqlQueryBuilderUtils.ANN_JOIN_COLUMNS);
            if (joinColumnsHolder != null) {
                onLeftColumns.addAll(joinColumnsHolder.getAnnotations(VALUE_MEMBER)
                        .stream()
                        .flatMap(ann -> ann.stringValue(isOwner ? "name" : REFERENCED_COLUMN_NAME).stream())
                        .toList());
                onRightColumns.addAll(joinColumnsHolder.getAnnotations(VALUE_MEMBER)
                        .stream()
                        .flatMap(ann -> ann.stringValue(isOwner ? REFERENCED_COLUMN_NAME : "name").stream())
                        .toList());
            }
        }
        if (onLeftColumns.isEmpty()) {
            PersistentEntityUtils.traversePersistentProperties(leftProperty, (associations, p) -> {
                String column = getMappedName(getNamingStrategy(leftProperty.getOwner()), merge(leftPropertyPath.getAssociations(), associations), p);
                onLeftColumns.add(column);
            });
            if (onLeftColumns.isEmpty()) {
                throw new MappingException("Cannot join on entity [" + leftProperty.getOwner().getName() + "] that has no declared ID");
            }
        }
        if (onRightColumns.isEmpty()) {
            PersistentEntityUtils.traversePersistentProperties(rightProperty, (associations, p) -> {
                String column = getMappedName(getNamingStrategy(rightProperty.getOwner()), merge(rightPropertyPath.getAssociations(), associations), p);
                onRightColumns.add(column);
            });
        }
        if (queryState.baseQueryDefinition().persistentEntity().getAnnotationMetadata().hasAnnotation(JsonSubView.class)) {
            sb.append(WHERE_CLAUSE);
        }
        join(sb,
            queryState.baseQueryDefinition(),
            joinType,
            getTableName(associatedEntity),
            rightTableAlias,
            leftTableAlias,
            escape ? onLeftColumns.stream().map(this::quote).toList() : onLeftColumns,
            escape ? onRightColumns.stream().map(this::quote).toList() : onRightColumns);
    }

    private void join(StringBuilder builder,
                      BaseQueryDefinition queryDefinition,
                      String joinType,
                      String tableName,
                      String tableAlias,
                      String onTableName,
                      List<String> onLeftColumns,
                      List<String> onRightColumns) {

        if (onLeftColumns.size() != onRightColumns.size()) {
            throw new IllegalStateException("Un-matching join columns size: " + onLeftColumns.size() + " != " + onRightColumns.size() + " " + onLeftColumns + ", " + onRightColumns);
        }

        if (!queryDefinition.persistentEntity().getAnnotationMetadata().hasAnnotation(JsonSubView.class)) {
            builder
                .append(joinType)
                .append(tableName)
                .append(SPACE)
                .append(tableAlias);
            if (queryDefinition instanceof SelectQueryDefinition selectQueryDefinition) {
                appendForUpdate(QueryPosition.AFTER_TABLE_NAME, selectQueryDefinition, builder);
            }
            builder.append(" ON ");
        }

        buildJoinColumnMatchPart(builder, tableAlias, onTableName, onLeftColumns, onRightColumns);
    }

    private void buildJoinColumnMatchPart(StringBuilder builder,
                                          String tableAlias,
                                          String onTableName,
                                          List<String> onLeftColumns,
                                          List<String> onRightColumns) {
        for (int i = 0; i < onLeftColumns.size(); i++) {
            String leftColumn = onLeftColumns.get(i);
            String rightColumn = onRightColumns.get(i);
            builder.append(onTableName)
                .append(DOT)
                .append(leftColumn)
                .append('=')
                .append(tableAlias)
                .append(DOT)
                .append(rightColumn);
            if (i + 1 != onLeftColumns.size()) {
                builder.append(LOGICAL_AND);
            }
        }
    }

    private PersistentAssociationPath createAssociationPath(PersistentEntity entity, Association association) {
        if (entity.getPropertyByName(association.getName()) != null) {
            PersistentProperty property = entity.getPropertyByName(association.getName());
            if (property != null) {
                return new PersistentAssociationPath(
                    new ArrayList<>(),
                    (Association) property
                );
            }
        }
        return new PersistentAssociationPath(
            new ArrayList<>(),
            association
        );
    }

    private QueryState createQueryState(PersistentEntity entity) {
        return new QueryState(new BaseQueryDefinition() {
            @Override
            public PersistentEntity persistentEntity() {
                return entity;
            }

            @Override
            public Predicate predicate() {
                // No extra WHERE; return a no-op predicate for SQL rendering
                return EMPTY_PREDICATE;
            }

            @Override
            public Collection<JoinPath> getJoinPaths() {
                return List.of();
            }

            @Override
            public Optional<JoinPath> getJoinPath(String path) {
                return Optional.empty();
            }
        }, true, true);
    }

    private <T> List<T> merge(List<T> left, List<T> right) {
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        List<T> associations = new ArrayList<>(left.size() + right.size());
        associations.addAll(left);
        associations.addAll(right);
        return associations;
    }

    @Override
    protected String quote(String persistedName, boolean supportsDynamicValues) {
        return switch (dialect) {
            case MYSQL, H2 -> '`' + persistedName + '`';
            case SQL_SERVER -> '[' + persistedName + ']';
            case ORACLE -> {
                // Oracle requires quoted identifiers to be in upper case
                String result = supportsDynamicValues ? SqlQueryBuilderUtils.mapPersistedName(persistedName, s -> s.toUpperCase(Locale.ENGLISH)) : persistedName.toUpperCase(Locale.ENGLISH);
                yield '"' + result + '"';
            }
            default -> '"' + persistedName + '"';
        };
    }

    @Override
    public String getColumnName(PersistentProperty persistentProperty) {
        return persistentProperty.getPersistedName();
    }

    @Override
    protected void appendForUpdate(QueryPosition queryPosition, SelectQueryDefinition definition, StringBuilder queryBuilder) {
        if (definition.isForUpdate()) {
            boolean isSqlServer = Dialect.SQL_SERVER.equals(dialect);
            if (isSqlServer && queryPosition.equals(QueryPosition.AFTER_TABLE_NAME) ||
                !isSqlServer && queryPosition.equals(QueryPosition.END_OF_QUERY)) {
                queryBuilder.append(isSqlServer ? SQL_SERVER_FOR_UPDATE_CLAUSE : STANDARD_FOR_UPDATE_CLAUSE);
            }
        }
    }

    @Override
    protected boolean computePropertyPaths() {
        return true;
    }

    @Override
    protected boolean isAliasForBatch(PersistentEntity persistentEntity, AnnotationMetadata annotationMetadata) {
        return isJsonEntity(annotationMetadata, persistentEntity);
    }

    @Override
    public Placeholder formatParameter(int index) {
        DialectConfig dialectConfig = perDialectConfig.get(dialect);
        String name;
        if (dialectConfig != null && dialectConfig.positionalNameFormatter != null) {
            name = String.format(dialectConfig.positionalNameFormatter, index);
        } else {
            name = String.valueOf(index);
        }
        if (dialectConfig != null && dialectConfig.positionalFormatter != null) {
            return new Placeholder(String.format(dialectConfig.positionalFormatter, name), name);
        } else {
            return new Placeholder("?", name);
        }
    }

    /**
     * Selects the default fallback strategy. For a generated value.
     *
     * @param property The Persistent property
     * @return The generated value
     */
    protected GeneratedValue.Type selectAutoStrategy(PersistentProperty property) {
        return defaultSelectAutoStrategy(property.getDataType(), dialect);
    }

    private GeneratedValue.Type defaultSelectAutoStrategy(DataType dataType, Dialect dialect) {
        if (dataType == DataType.UUID) {
            return UUID;
        }
        if (dialect == Dialect.ORACLE) {
            return SEQUENCE;
        }
        return AUTO;
    }

    /**
     * @return The positional parameter format
     */
    public final String positionalParameterFormat() {
        DialectConfig dialectConfig = perDialectConfig.get(dialect);
        if (dialectConfig != null && dialectConfig.positionalFormatter != null) {
            return dialectConfig.positionalFormatter;
        }
        return DEFAULT_POSITIONAL_PARAMETER_MARKER;
    }

    @Override
    protected void appendLimitAndOrder(AnnotationMetadata annotationMetadata,
                                       SelectQueryDefinition definition,
                                       boolean appendLimit,
                                       boolean appendOrder,
                                       QueryState queryState) {
        if (appendOrder) {
            appendOrder(annotationMetadata, definition, queryState);
        }
        if (appendLimit) {
            appendLimitAndOffset(getDialect(), definition.limit(), definition.offset(), queryState.getQuery());
        }
        Map<Integer, String> parametersInRole = definition.parametersInRole();
       if (parameterInRoleModifiesOrder(parametersInRole) || parameterInRoleModifiesLimit(parametersInRole)) {
            Map.Entry<Integer, String> e = parametersInRole.entrySet().iterator().next();
            queryState.pushParameter(new QueryParameterBinding() {
                @Override
                public String getName() {
                    return e.getValue();
                }

                @Override
                public String getKey() {
                    return "";
                }

                @Override
                public int getParameterIndex() {
                    return e.getKey();
                }

                @Override
                public DataType getDataType() {
                    return DataType.OBJECT;
                }

                @Override
                public boolean isExpandable() {
                    return true;
                }

                @Override
                public String getRole() {
                    return e.getValue();
                }

                @Override
                @Nullable
                public String getTableAlias() {
                    String rootAlias = queryState.getRootAlias();
                    return StringUtils.isNotEmpty(rootAlias) ? rootAlias : null;
                }
            });
        }
    }

    private void appendOrder(AnnotationMetadata annotationMetadata, SelectQueryDefinition definition, QueryState queryState) {
        List<Order> orders = definition.order();
        if (getDialect() == Dialect.SQL_SERVER && orders.isEmpty() && (definition.limit() > 0 || definition.offset() > 0)) {
            PersistentEntity persistentEntity = definition.persistentEntity();
            if (!persistentEntity.hasIdentity()) {
                throw new DataAccessException("Pagination requires an entity ID on SQL Server");
            }
            PersistentProperty identity = persistentEntity.getIdentity();
            orders = List.of(new DefaultOrder<>(new DefaultPersistentPropertyPath<>(identity, List.of()), true, false));
        }
        appendOrder(annotationMetadata, orders, queryState);
    }

    private <T> void addToCollectionIfNotContains(Collection<T> collection, T item) {
        if (collection.contains(item)) {
            return;
        }
        collection.add(item);
    }

    private static final class DialectConfig {
        @Nullable
        Boolean escapeQueries;
        @Nullable
        String positionalFormatter;
        @Nullable
        String positionalNameFormatter;
    }

    protected class SqlSelectionVisitor extends AbstractSqlLikeQueryBuilder.SqlSelectionVisitor {

        public SqlSelectionVisitor(QueryState queryState, AnnotationMetadata annotationMetadata, boolean distinct) {
            super(queryState, annotationMetadata, distinct);
        }

        @Override
        protected void appendRowCount(String logicalName) {
            query.append("COUNT(*)");
        }

        @Override
        protected void appendRowCountDistinct(String logicalName) {
            query.append("COUNT(DISTINCT(");
            // If id is composite identity or embedded id then we need to do CONCAT
            // all id properties. It is safe as none portion of such id should be null
            // For regular single field id we just select that field COUNT(DISTINCT(id_field))
            // and we are doing CONCAT because COUNT(DISTINCT *) is not supported
            if (entity.hasCompositeIdentity()) {
                appendConcatProperties(List.of(entity.getCompositeIdentity()));
            } else if (entity.hasIdentity()) {
                List<PersistentProperty> identityProperties = entity.getIdentityProperties();
                if (identityProperties.isEmpty()) {
                    throw new IllegalArgumentException(CANNOT_QUERY_ON_ID_WITH_ENTITY_THAT_HAS_NO_ID);
                }
                long count = identityProperties.stream().mapToInt(PersistentEntityUtils::countPersistentProperties).sum();
                if (count > 1) {
                    appendConcatProperties(identityProperties);
                } else {
                    for (PersistentProperty identity : identityProperties) {
                        appendPropertyProjection(asQueryPropertyPath(tableAlias, identity));
                    }
                }
            } else {
                throw new IllegalArgumentException(CANNOT_QUERY_ON_ID_WITH_ENTITY_THAT_HAS_NO_ID);
            }
            query.append("))");
        }

        private void appendConcatProperties(List<PersistentProperty> properties) {
            query.append(" CONCAT(");
            for (Iterator<PersistentProperty> iterator = properties.iterator(); iterator.hasNext();) {
                PersistentProperty identity = iterator.next();
                appendPropertyProjection(asQueryPropertyPath(tableAlias, identity));
                if (iterator.hasNext()) {
                    query.append(COMMA);
                }
            }
            query.append(CLOSE_BRACKET);
        }

        @Override
        protected void selectAllColumnsAndJoined() {
            selectAllColumns(annotationMetadata, entity, tableAlias);

            Collection<JoinPath> allPaths = queryState.baseQueryDefinition().getJoinPaths();
            selectAllColumnsFromJoinPaths(allPaths, null);
        }

        @Internal
        @Override
        protected void selectAllColumnsFromJoinPaths(Collection<JoinPath> allPaths,
                                                     @Nullable
                                                     Map<JoinPath, String> joinAliasOverride) {
            if (CollectionUtils.isEmpty(allPaths)) {
                return;
            }

            List<JoinPath> joinPaths = allPaths.stream().filter(jp -> jp.getJoinType().isFetch()).collect(Collectors.toList());
            Collections.reverse(joinPaths);

            if (CollectionUtils.isEmpty(joinPaths)) {
                return;
            }
            for (JoinPath joinPath : joinPaths) {
                Association association = joinPath.getAssociation();
                if (association.isEmbedded()) {
                    // joins on embedded don't make sense
                    continue;
                }

                PersistentEntity associatedEntity = association.getAssociatedEntity();
                NamingStrategy namingStrategy = getNamingStrategy(associatedEntity);

                String joinAlias = joinAliasOverride == null ? getAliasName(joinPath) : joinAliasOverride.get(joinPath);
                Objects.requireNonNull(joinAlias);
                String joinPathAlias = getPathOnlyAliasName(joinPath);

                query.append(COMMA);

                boolean includeIdentity = association.isForeignKey();
                // in the case of a foreign key association the ID is not in the table,
                // so we need to retrieve it
                PersistentEntityUtils.traversePersistentProperties(associatedEntity, includeIdentity, true, (propertyAssociations, prop) -> {

                    String transformed = getDataTransformerReadValue(joinAlias, prop).orElse(null);
                    String columnAlias = getColumnAlias(prop);
                    String columnName;
                    if (computePropertyPaths()) {
                        columnName = getMappedName(namingStrategy, propertyAssociations, prop);
                    } else {
                        columnName = asPath(propertyAssociations, prop);
                    }
                    if (transformed != null) {
                        query.append(transformed).append(AS_CLAUSE);
                    } else {
                        query
                            .append(joinAlias)
                            .append(DOT)
                            .append(queryState.shouldEscape() ? quote(columnName) : columnName)
                            .append(AS_CLAUSE);
                    }
                    if (StringUtils.isNotEmpty(columnAlias)) {
                        query.append(columnAlias);
                    } else {
                        query.append(joinPathAlias).append(columnName);
                    }
                    query.append(COMMA);
                });
                query.setLength(query.length() - 1);
            }
        }

        /**
         * Selects all columns for the given entity and alias.
         *
         * @param annotationMetadata The annotation metadata
         * @param entity             The entity
         * @param alias              The alias
         */
        @Override
        public void selectAllColumns(AnnotationMetadata annotationMetadata, PersistentEntity entity, @Nullable String alias) {
            if (canUseWildcardForSelect(annotationMetadata, entity)) {
                selectAllColumns(query, alias);
                return;
            }
            boolean escape = shouldEscape(entity);
            NamingStrategy namingStrategy = getNamingStrategy(entity);
            int length = query.length();
            PersistentEntityUtils.traversePersistentProperties(entity, (associations, property)
                -> appendProperty(query, associations, property, namingStrategy, alias, escape));
            int newLength = query.length();
            if (newLength == length) {
                selectAllColumns(query, alias);
            } else {
                query.setLength(newLength - 1);
            }
        }

        /**
         * Appends '*' symbol (meaning all columns selection) to the string builder representing query.
         *
         * @param sb    the string builder representing query
         * @param alias an alias, if not null will be apended with '.' before '*' symbol
         */
        private void selectAllColumns(StringBuilder sb, @Nullable String alias) {
            if (alias != null) {
                sb.append(alias).append(DOT);
            }
            sb.append("*");
        }

        private boolean canUseWildcardForSelect(AnnotationMetadata annotationMetadata, PersistentEntity entity) {
            if (isJsonEntity(annotationMetadata, entity)) {
                return true;
            }
            return Stream.concat(entity.getIdentityProperties().stream(), entity.getPersistentProperties().stream())
                .flatMap(SqlQueryBuilderUtils::flatMapEmbedded)
                .noneMatch(pp -> {
                    if (pp instanceof Association association) {
                        return !association.isForeignKey();
                    }
                    return true;
                });
        }

    }

    /**
     * Default implementation of {@link ReturningSelectionVisitor} used by {@link SqlQueryBuilder}
     * to render the projection for SQL RETURNING clauses (INSERT/UPDATE/DELETE).
     * <p>
     * In addition to emitting the selection into the SQL buffer, this visitor collects:
     * <ul>
     *   <li>Unescaped column names in declaration order via {@link #getUnescapedColumns()}</li>
     *   <li>Result column data types via {@link #getResultColumnTypes()}</li>
     * </ul>
     * The collected metadata is used by dialects such as Oracle that require {@code RETURNING ... INTO}
     * OUT parameters instead of a result set.
     * <p>
     * This type is not thread-safe and is intended for per-query use only.
     * It is an internal implementation detail and not part of the public API.
     *
     * @see SqlQueryBuilder#createReturningSelectionVisitor(AnnotationMetadata, QueryState, boolean)
     * @see ReturningSelectionVisitor
     */
    @Internal
    protected final class DefaultReturningSelectionVisitor extends SqlSelectionVisitor implements ReturningSelectionVisitor {
        private final List<String> unescapedColumns = new ArrayList<>();
        private final List<DataType> resultColumnTypes = new ArrayList<>();

        DefaultReturningSelectionVisitor(QueryState queryState, AnnotationMetadata annotationMetadata, boolean distinct) {
            super(queryState, annotationMetadata, distinct);
        }

        @Override
        public List<String> getUnescapedColumns() {
            return unescapedColumns;
        }

        @Override
        public List<DataType> getResultColumnTypes() {
            return resultColumnTypes;
        }

        @Override
        public void selectAllColumns(AnnotationMetadata annotationMetadata, PersistentEntity entity, @Nullable String alias) {
            // Mirror base behavior, but also collect unescaped column names and types for OUT parameter metadata
            boolean escape = shouldEscape(entity);
            NamingStrategy namingStrategy = getNamingStrategy(entity);
            int length = query.length();
            PersistentEntityUtils.traversePersistentProperties(entity, (associations, property) -> {
                appendProperty(query, associations, property, namingStrategy, alias, escape);
                unescapedColumns.add(getMappedName(namingStrategy, associations, property));
                resultColumnTypes.add(property.getDataType());
            });
            int newLength = query.length();
            if (newLength == length) {
                // Fallback to wildcard if no properties were appended (shouldn't normally happen for non-JSON entities)
                if (alias != null) {
                    query.append(alias).append(DOT);
                }
                query.append("*");
            } else {
                query.setLength(newLength - 1);
            }
        }

        @Override
        protected void appendPropertyProjection(QueryPropertyPath propertyPath) {
            boolean jsonEntity = isJsonEntity(annotationMetadata, entity);
            if (!computePropertyPaths() || jsonEntity) {
                // Delegate to default rendering; collect best-effort name/type
                super.appendPropertyProjection(propertyPath);
                PersistentProperty prop = propertyPath.getPropertyPath().getProperty();
                unescapedColumns.add(prop.getPersistedName());
                resultColumnTypes.add(prop.getDataType());
                return;
            }
            String tableAlias = propertyPath.getTableAlias();
            boolean escape = propertyPath.shouldEscape();
            NamingStrategy namingStrategy = propertyPath.getNamingStrategy();
            boolean[] needsTrimming = {false};
            int[] propertiesCount = new int[1];

            PersistentEntityUtils.traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), traverseEmbedded(), (associations, property) -> {
                appendProperty(query, associations, property, namingStrategy, tableAlias, escape);
                unescapedColumns.add(getMappedName(namingStrategy, associations, property));
                resultColumnTypes.add(property.getDataType());
                needsTrimming[0] = true;
                propertiesCount[0]++;
            });
            if (needsTrimming[0]) {
                query.setLength(query.length() - 1);
            }
            if (StringUtils.isNotEmpty(columnAlias)) {
                if (propertiesCount[0] > 1) {
                    throw new IllegalStateException("Cannot apply a column alias: " + columnAlias + " with expanded property: " + propertyPath);
                }
                if (propertiesCount[0] == 1) {
                    query.append(AS_CLAUSE).append(columnAlias);
                }
            }
        }
    }

}
