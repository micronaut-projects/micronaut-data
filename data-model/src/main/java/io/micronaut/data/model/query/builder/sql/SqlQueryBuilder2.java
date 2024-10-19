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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.EntityRepresentation;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinColumns;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.PersistentAssociationPath;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.DefaultPersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.PersistentPropertyOrder;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Selection;

import java.lang.annotation.Annotation;
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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.micronaut.data.annotation.GeneratedValue.Type.AUTO;
import static io.micronaut.data.annotation.GeneratedValue.Type.IDENTITY;
import static io.micronaut.data.annotation.GeneratedValue.Type.SEQUENCE;
import static io.micronaut.data.annotation.GeneratedValue.Type.UUID;
import static io.micronaut.data.model.query.builder.sql.SqlQueryBuilderUtils.addTypeToColumn;

/**
 * Implementation of {@link QueryBuilder} that builds SQL queries.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
@Internal
@SuppressWarnings("FileLength")
public class SqlQueryBuilder2 extends AbstractSqlLikeQueryBuilder2 {

    /**
     * The start of an IN expression.
     */
    public static final String DEFAULT_POSITIONAL_PARAMETER_MARKER = "?";

    public static final String STANDARD_FOR_UPDATE_CLAUSE = " FOR UPDATE";
    public static final String SQL_SERVER_FOR_UPDATE_CLAUSE = " WITH (UPDLOCK, ROWLOCK)";

    /**
     * Annotation used to represent join tables.
     */
    private static final String ANN_JOIN_TABLE = "io.micronaut.data.annotation.sql.JoinTable";
    private static final String ANN_JOIN_COLUMNS = "io.micronaut.data.annotation.sql.JoinColumns";
    private static final String VALUE_MEMBER = "value";
    private static final String BLANK_SPACE = " ";
    private static final String SEQ_SUFFIX = "_seq";
    private static final String INSERT_INTO = "INSERT INTO ";
    private static final String JDBC_REPO_ANNOTATION = "io.micronaut.data.jdbc.annotation.JdbcRepository";

    private final Dialect dialect;
    private final Map<Dialect, DialectConfig> perDialectConfig = new EnumMap<>(Dialect.class);

    /**
     * Constructor with annotation metadata.
     *
     * @param annotationMetadata The annotation metadata
     */
    @Creator
    public SqlQueryBuilder2(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata != null) {
            this.dialect = annotationMetadata
                .enumValue(JDBC_REPO_ANNOTATION, "dialect", Dialect.class)
                .orElseGet(() ->
                    annotationMetadata
                        .enumValue(Repository.class, "dialect", Dialect.class)
                        .orElse(Dialect.ANSI)
                );

            AnnotationValue<SqlQueryConfiguration> annotation = annotationMetadata.getAnnotation(SqlQueryConfiguration.class);
            if (annotation != null) {
                List<AnnotationValue<SqlQueryConfiguration.DialectConfiguration>> dialectConfigs = annotation.getAnnotations(AnnotationMetadata.VALUE_MEMBER, SqlQueryConfiguration.DialectConfiguration.class);
                for (AnnotationValue<SqlQueryConfiguration.DialectConfiguration> dialectConfig : dialectConfigs) {
                    dialectConfig.enumValue("dialect", Dialect.class).ifPresent(dialect -> {
                        DialectConfig dc = new DialectConfig();
                        perDialectConfig.put(dialect, dc);
                        dialectConfig.stringValue("positionalParameterFormat").ifPresent(format ->
                            dc.positionalFormatter = format
                        );
                        dialectConfig.stringValue("positionalParameterName").ifPresent(format ->
                            dc.positionalNameFormatter = format
                        );
                        dialectConfig.booleanValue("escapeQueries").ifPresent(escape ->
                            dc.escapeQueries = escape
                        );
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
    public SqlQueryBuilder2() {
        this.dialect = Dialect.ANSI;
    }

    /**
     * @param dialect The dialect
     */
    public SqlQueryBuilder2(Dialect dialect) {
        ArgumentUtils.requireNonNull("dialect", dialect);
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
    protected boolean shouldEscape(@NonNull PersistentEntity entity) {
        DialectConfig config = perDialectConfig.get(dialect);
        if (config != null && config.escapeQueries != null) {
            return config.escapeQueries;
        } else {
            return super.shouldEscape(entity);
        }
    }

    @Override
    protected String asLiteral(Object value) {
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
    @NonNull
    public String buildBatchCreateTableStatement(@NonNull PersistentEntity... entities) {
        return Arrays.stream(entities).flatMap(entity -> Stream.of(buildCreateTableStatements(entity)))
            .collect(Collectors.joining(System.getProperty("line.separator")));
    }

    /**
     * Builds a batch drop tables statement. Designed for testing and not production usage. For production a
     * SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entities the entities
     * @return The table
     */
    @Experimental
    @NonNull
    public String buildBatchDropTableStatement(@NonNull PersistentEntity... entities) {
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
    @NonNull
    public String[] buildDropTableStatements(@NonNull PersistentEntity entity) {
        String tableName = getTableName(entity);
        boolean escape = shouldEscape(entity);
        String sql = "DROP TABLE " + tableName;
        Collection<Association> foreignKeyAssociations = getJoinTableAssociations(entity);
        List<String> dropStatements = new ArrayList<>();
        for (Association association : foreignKeyAssociations) {
            AnnotationMetadata associationMetadata = association.getAnnotationMetadata();
            NamingStrategy namingStrategy = getNamingStrategy(entity);
            String joinTableName = associationMetadata
                .stringValue(ANN_JOIN_TABLE, "name")
                .orElseGet(() ->
                    getMappedName(namingStrategy, association)
                );
            dropStatements.add("DROP TABLE " + (escape ? quote(joinTableName) : joinTableName) + ";");
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
    @NonNull
    public String buildJoinTableInsert(@NonNull PersistentEntity entity, @NonNull Association association) {
        if (!isForeignKeyWithJoinTable(association)) {
            throw new IllegalArgumentException("Join table inserts can only be built for foreign key associations that are mapped with a join table.");
        }
        Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
        Association owningAssociation = inverseSide.orElse(association);
        AnnotationMetadata annotationMetadata = owningAssociation.getAnnotationMetadata();
        NamingStrategy namingStrategy = getNamingStrategy(entity);
        String joinTableName = annotationMetadata
            .stringValue(ANN_JOIN_TABLE, "name")
            .orElseGet(() ->
                getMappedName(namingStrategy, association)
            );
        joinTableName = quote(joinTableName);
        String joinTableSchema = annotationMetadata
            .stringValue(ANN_JOIN_TABLE, SqlMembers.SCHEMA)
            .orElse(getSchemaName(entity));
        if (StringUtils.isNotEmpty(joinTableSchema)) {
            joinTableSchema = quote(joinTableSchema);
            joinTableName = joinTableSchema + DOT + joinTableName;
        }
        List<String> leftJoinColumns = resolveJoinTableJoinColumns(annotationMetadata, true, entity, namingStrategy);
        List<String> rightJoinColumns = resolveJoinTableJoinColumns(annotationMetadata, false, association.getAssociatedEntity(), namingStrategy);
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
    public static boolean isForeignKeyWithJoinTable(@NonNull Association association) {
        if (!association.isForeignKey()) {
            return false;
        }
        if (association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").isPresent()) {
            return false;
        }
        AnnotationValue<JoinColumns> joinColumnsAnnotationValue = association.getAnnotationMetadata().getAnnotation(JoinColumns.class);
        return joinColumnsAnnotationValue == null || CollectionUtils.isEmpty(joinColumnsAnnotationValue.getAnnotations(VALUE_MEMBER));
    }

    /**
     * Builds the creation table statement. Designed for testing and not production usage. For production a
     * SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entity The entity
     * @return The tables for the give entity
     */
    @Experimental
    @NonNull
    public String[] buildCreateTableStatements(@NonNull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);
        final String unescapedTableName = getUnescapedTableName(entity);
        String tableName = getTableName(entity);
        boolean escape = shouldEscape(entity);

        List<String> createStatements = new ArrayList<>();
        String schema = getSchemaName(entity);
        if (StringUtils.isNotEmpty(schema)) {
            if (escape) {
                schema = quote(schema);
            }
            createStatements.add("CREATE SCHEMA " + schema + ";");
        }

        Collection<Association> foreignKeyAssociations = getJoinTableAssociations(entity);

        NamingStrategy namingStrategy = getNamingStrategy(entity);
        if (CollectionUtils.isNotEmpty(foreignKeyAssociations)) {
            for (Association association : foreignKeyAssociations) {
                StringBuilder joinTableBuilder = new StringBuilder("CREATE TABLE ");
                PersistentEntity associatedEntity = association.getAssociatedEntity();

                Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
                Association owningAssociation = inverseSide.orElse(association);
                AnnotationMetadata annotationMetadata = owningAssociation.getAnnotationMetadata();

                String joinTableName = annotationMetadata
                    .stringValue(ANN_JOIN_TABLE, "name")
                    .orElseGet(() ->
                        getMappedName(namingStrategy, association)
                    );
                if (escape) {
                    joinTableName = quote(joinTableName);
                }
                String joinTableSchema = annotationMetadata.stringValue(ANN_JOIN_TABLE, SqlMembers.SCHEMA).orElse(null);
                if (StringUtils.isNotEmpty(joinTableSchema)) {
                    if (escape) {
                        joinTableSchema = quote(joinTableSchema);
                    }
                } else {
                    joinTableSchema = schema;
                }
                if (StringUtils.isNotEmpty(joinTableSchema)) {
                    joinTableBuilder.append(joinTableSchema).append(DOT);
                }
                joinTableBuilder.append(joinTableName).append(" (");
                List<PersistentPropertyPath> leftProperties = new ArrayList<>();
                List<PersistentPropertyPath> rightProperties = new ArrayList<>();
                boolean isAssociationOwner = inverseSide.isEmpty();
                List<String> leftJoinTableColumns = resolveJoinTableJoinColumns(annotationMetadata, isAssociationOwner, entity, namingStrategy);
                List<String> rightJoinTableColumns = resolveJoinTableJoinColumns(annotationMetadata, !isAssociationOwner, association.getAssociatedEntity(), namingStrategy);
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
                        if (escape) {
                            columnName = quote(columnName);
                        }
                        joinTableBuilder
                            .append(addTypeToColumn(pp.getProperty(), columnName, dialect, true))
                            .append(',');
                    }
                } else {
                    for (PersistentPropertyPath pp : leftProperties) {
                        String columnName = namingStrategy.mappedJoinTableColumn(entity, pp.getAssociations(), pp.getProperty());
                        if (escape) {
                            columnName = quote(columnName);
                        }
                        joinTableBuilder
                            .append(addTypeToColumn(pp.getProperty(), columnName, dialect, true))
                            .append(',');
                    }
                }
                if (rightJoinTableColumns.size() == rightProperties.size()) {
                    for (int i = 0; i < rightJoinTableColumns.size(); i++) {
                        PersistentPropertyPath pp = rightProperties.get(i);
                        String columnName = rightJoinTableColumns.get(i);
                        if (escape) {
                            columnName = quote(columnName);
                        }
                        joinTableBuilder
                            .append(addTypeToColumn(pp.getProperty(), columnName, dialect, true))
                            .append(',');
                    }
                } else {
                    for (PersistentPropertyPath pp : rightProperties) {
                        String columnName = namingStrategy.mappedJoinTableColumn(entity, pp.getAssociations(), pp.getProperty());
                        if (escape) {
                            columnName = quote(columnName);
                        }
                        joinTableBuilder
                            .append(addTypeToColumn(pp.getProperty(), columnName, dialect, true))
                            .append(',');
                    }
                }
                joinTableBuilder.setLength(joinTableBuilder.length() - 1);
                joinTableBuilder.append(")");
                if (dialect != Dialect.ORACLE) {
                    joinTableBuilder.append(';');
                }

                createStatements.add(joinTableBuilder.toString());

            }
        }

        boolean generatePkAfterColumns = false;

        List<String> primaryColumnsName = new ArrayList<>();
        List<String> columns = new ArrayList<>();

        List<PersistentProperty> identities = entity.getIdentityProperties();
        for (PersistentProperty identity : identities) {
            List<PersistentPropertyPath> ids = new ArrayList<>();
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property)
                -> ids.add(PersistentPropertyPath.of(associations, property, "")));
            int idFieldCount = ids.size();
            if (idFieldCount > 1) {
                generatePkAfterColumns = true;
            } else if (idFieldCount > 0 && !identity.isGenerated()) {
                // Need to define primary key if id not generated (otherwise defined in column definition)
                // but can't do if id field is byte array (BLOB) and it expects length for MySQL
                if (!(dialect == Dialect.MYSQL && ids.get(0).getProperty().getDataType() == DataType.BYTE_ARRAY)) {
                    generatePkAfterColumns = true;
                }
            }
            boolean finalGeneratePkAfterColumns = generatePkAfterColumns;
            for (PersistentPropertyPath pp : ids) {
                String column = getMappedName(namingStrategy, pp.getAssociations(), pp.getProperty());
                if (escape) {
                    column = quote(column);
                }
                primaryColumnsName.add(column);

                column = addTypeToColumn(pp.getProperty(), column, dialect, isRequired(pp.getAssociations(), pp.getProperty()));
                if (isNotForeign(pp.getAssociations())) {
                    column = addGeneratedStatementToColumn(pp.getProperty(), column, !finalGeneratePkAfterColumns);
                }
                columns.add(column);
            }
        }

        PersistentProperty version = entity.getVersion();
        if (version != null && !version.isGenerated()) {
            String column = getMappedName(namingStrategy, Collections.emptyList(), version);
            if (escape) {
                column = quote(column);
            }
            column = addTypeToColumn(version, column, dialect, true);
            columns.add(column);
        }

        BiConsumer<List<Association>, PersistentProperty> addColumn = (associations, property) -> {
            String column = getMappedName(namingStrategy, associations, property);
            if (escape) {
                column = quote(column);
            }
            column = addTypeToColumn(property, column, dialect, isRequired(associations, property));
            if (isNotForeign(associations)) {
                column = addGeneratedStatementToColumn(property, column, false);
            }
            columns.add(column);
        };

        for (PersistentProperty prop : entity.getPersistentProperties()) {
            PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), prop, addColumn);
        }

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

        for (PersistentProperty identity : identities) {
            if (identity.isGenerated()) {
                GeneratedValue.Type idGeneratorType = identity.getAnnotationMetadata()
                    .enumValue(GeneratedValue.class, GeneratedValue.Type.class)
                    .orElseGet(() -> selectAutoStrategy(identity));
                boolean isSequence = idGeneratorType == GeneratedValue.Type.SEQUENCE;
                final String generatedDefinition = identity.getAnnotationMetadata().stringValue(GeneratedValue.class, "definition").orElse(null);
                if (generatedDefinition != null) {
                    createStatements.add(generatedDefinition);
                } else if (isSequence) {
                    final boolean isSqlServer = dialect == Dialect.SQL_SERVER;
                    final String sequenceName = quote(unescapedTableName + SEQ_SUFFIX);
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
                    createStatements.add(createSequenceStmt);
                }
            }
        }

        createStatements.add(builder.toString());
        addIndexes(entity, tableName, createStatements);
        return createStatements.toArray(new String[0]);
    }

    private void addIndexes(PersistentEntity entity, String tableName, List<String> createStatements) {
        final List<String> indexes = createIndexes(entity, tableName);
        if (CollectionUtils.isNotEmpty(indexes)) {
            createStatements.addAll(indexes);
        }
    }

    private List<String> createIndexes(PersistentEntity entity, String tableName) {
        List<String> indexStatements = new ArrayList<>();

        final Optional<List<AnnotationValue<Index>>> indexes = entity
            .findAnnotation(Indexes.class)
            .map(idxes -> idxes.getAnnotations(VALUE_MEMBER, Index.class));

        Stream.of(indexes)
            .flatMap(Optional::stream)
            .flatMap(Collection::stream)
            .forEach(index -> indexStatements.add(addIndex(entity, new IndexConfiguration(index, tableName, entity.getPersistedName()))));

        return indexStatements;

    }

    private String addIndex(PersistentEntity entity, IndexConfiguration config) {
        // Create index name without escaped table name and then escape if needed
        String indexName = config.index.stringValue("name")
            .orElse(String.format(
                "idx_%s%s", prepareNames(config.unquotedTableName),
                makeTransformedColumnList(provideColumnList(config))));
        if (shouldEscape(entity)) {
            indexName = quote(indexName);
        }

        StringBuilder indexBuilder = new StringBuilder();
        indexBuilder.append("CREATE ").append(config.index.booleanValue("unique")
                .map(isUnique -> isUnique ? "UNIQUE " : "")
                .orElse(""))
            .append("INDEX ");
        indexBuilder.append(indexName).append(" ON ").append(Optional.ofNullable(config.tableName)
            .orElseThrow(() -> new NullPointerException("Table name cannot be null"))).append(" (").append(provideColumnList(config));

        if (dialect == Dialect.ORACLE) {
            indexBuilder.append(")");
        } else {
            indexBuilder.append(");");
        }
        return indexBuilder.toString();
    }

    private String provideColumnList(IndexConfiguration config) {
        return String.join(", ", (String[]) config.index.getValues().get("columns"));
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

    private boolean isRequired(List<Association> associations, PersistentProperty property) {
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

    private boolean isNotForeign(List<Association> associations) {
        for (Association association : associations) {
            if (association.getKind() != Relation.Kind.EMBEDDED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected String getTableAsKeyword() {
        return BLANK_SPACE;
    }

    private String addGeneratedStatementToColumn(PersistentProperty prop, String column, boolean isPk) {
        if (prop.isGenerated()) {
            GeneratedValue.Type type = prop.getAnnotationMetadata().enumValue(GeneratedValue.class, GeneratedValue.Type.class)
                .orElse(AUTO);

            if (type == AUTO) {
                if (prop.getDataType() == DataType.UUID) {
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
                            column += " GENERATED ALWAYS AS IDENTITY (MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE)";
                        } else {
                            column += " NOT NULL";
                        }
                    } else {
                        column += " NOT NULL";
                    }
                    break;
                default:
                    if (type == UUID) {
                        // mysql requires the UUID generation in the insert statement
                        if (dialect != Dialect.MYSQL) {
                            column += " NOT NULL DEFAULT random_uuid()";
                        } else {
                            column += " NOT NULL";
                        }
                    } else {
                        column += " AUTO_INCREMENT";
                    }
            }
            if (isPk && !addPkBefore) {
                column += " PRIMARY KEY";
            }
        }
        return column;
    }

    @NonNull
    private List<String> resolveJoinTableJoinColumns(AnnotationMetadata annotationMetadata, boolean associationOwner, PersistentEntity entity, NamingStrategy namingStrategy) {
        List<String> joinColumns = getJoinedColumns(annotationMetadata, associationOwner, "name");
        if (!joinColumns.isEmpty()) {
            return joinColumns;
        }
        List<String> columns = new ArrayList<>();
        PersistentProperty property1 = entity.getIdentity();
        PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), property1, (associations, property)
            -> columns.add(namingStrategy.mappedJoinTableColumn(entity, associations, property)));
        return columns;
    }

    @NonNull
    private List<String> resolveJoinTableAssociatedColumns(AnnotationMetadata annotationMetadata, boolean associationOwner, PersistentEntity entity, NamingStrategy namingStrategy) {
        List<String> joinColumns = getJoinedColumns(annotationMetadata, associationOwner, "referencedColumnName");
        if (!joinColumns.isEmpty()) {
            return joinColumns;
        }
        PersistentProperty identity = entity.getIdentity();
        if (identity == null) {
            throw new MappingException("Cannot have a foreign key association without an ID on entity: " + entity.getName());
        }
        List<String> columns = new ArrayList<>();
        PersistentEntityUtils.traversePersistentProperties(identity, (associations, property) -> {
            String columnName = getMappedName(namingStrategy, associations, property);
            columns.add(columnName);
        });
        return columns;
    }

    @NonNull
    private List<String> getJoinedColumns(AnnotationMetadata annotationMetadata, boolean associationOwner, String columnType) {
        AnnotationValue<Annotation> joinTable = annotationMetadata.getAnnotation(ANN_JOIN_TABLE);
        if (joinTable != null) {
            return joinTable.getAnnotations(associationOwner ? "joinColumns" : "inverseJoinColumns")
                .stream()
                .flatMap(ann -> ann.stringValue(columnType).stream())
                .toList();
        }
        return Collections.emptyList();
    }

    @NonNull
    private Collection<Association> getJoinTableAssociations(PersistentEntity persistentEntity) {
        return Stream.concat(Stream.of(persistentEntity.getIdentity()), persistentEntity.getPersistentProperties().stream())
            .flatMap(SqlQueryBuilder2::flatMapEmbedded)
            .filter(p -> {
                if (p instanceof Association a) {
                    return isForeignKeyWithJoinTable(a);
                }
                return false;
            }).map(p -> (Association) p).toList();
    }

    @Override
    protected SqlSelectionVisitor createSelectionVisitor(AnnotationMetadata annotationMetadata, QueryState queryState, boolean distinct) {
        return new SqlSelectionVisitor(queryState, annotationMetadata, distinct);
    }

    private static Stream<? extends PersistentProperty> flatMapEmbedded(PersistentProperty pp) {
        if (pp instanceof Embedded embedded) {
            PersistentEntity embeddedEntity = embedded.getAssociatedEntity();
            return embeddedEntity.getPersistentProperties()
                .stream()
                .flatMap(SqlQueryBuilder2::flatMapEmbedded);
        }
        return Stream.of(pp);
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

    @NonNull
    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition definition) {
        Selection<?> returningSelection = definition.returningSelection();
        if (returningSelection != null && !getDialect().supportsInsertReturning()) {
            throw new IllegalStateException("Dialect: " + getDialect() + " doesn't support INSERT ... RETURNING clause");
        }
        PersistentEntity entity = definition.persistentEntity();

        boolean escape = shouldEscape(entity);
        final String unescapedTableName = getUnescapedTableName(entity);

        String builder;
        List<QueryParameterBinding> parameterBindings = new ArrayList<>();

        if (isJsonEntity(repositoryMetadata, entity)) {
            AnnotationValue<EntityRepresentation> entityRepresentationAnnotationValue = entity.getAnnotationMetadata().getAnnotation(EntityRepresentation.class);
            String columnName = entityRepresentationAnnotationValue.getRequiredValue("column", String.class);
            int key = 1;
            builder = INSERT_INTO + getTableName(entity) + " VALUES (" + formatParameter(key) + ")";
            for (PersistentProperty identity : entity.getIdentityProperties()) {
                if (identity.isGenerated()) {
                    String identityName = identity.getPersistedName();
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
            List<String> resultColumns = new ArrayList<>();
            List<String> values = new ArrayList<>();

            for (PersistentProperty prop : persistentProperties) {
                PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), prop, (associations, property) -> {
                    if (prop.isGenerated()) {
                        String columnName = getMappedName(namingStrategy, associations, property);
                        if (escape) {
                            columnName = quote(columnName);
                        }
                        resultColumns.add(columnName);
                        return;
                    }

                    addWriteExpression(values, prop);

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
                    if (escape) {
                        columnName = quote(columnName);
                    }
                    columns.add(columnName);
                    resultColumns.add(columnName);
                });
            }
            PersistentProperty version = entity.getVersion();
            if (version != null && !version.isGenerated()) {
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
                    public JsonDataType getJsonDataType() {
                        return null;
                    }

                    @Override
                    public String[] getPropertyPath() {
                        return new String[]{version.getName()};
                    }
                });

                String columnName = getMappedName(namingStrategy, Collections.emptyList(), version);
                if (escape) {
                    columnName = quote(columnName);
                }
                columns.add(columnName);
                resultColumns.add(columnName);
            }

            for (PersistentProperty identity : entity.getIdentityProperties()) {
                // Property skipped
                PersistentEntityUtils.traversePersistentProperties(Collections.emptyList(), identity, (associations, property) -> {
                    String columnName = getMappedName(namingStrategy, associations, property);
                    if (escape) {
                        columnName = quote(columnName);
                    }

                    boolean isSequence = false;
                    if (isNotForeign(associations)) {

                        resultColumns.add(columnName);

                        Optional<AnnotationValue<GeneratedValue>> generated = property.findAnnotation(GeneratedValue.class);
                        if (generated.isPresent()) {
                            GeneratedValue.Type idGeneratorType = generated
                                .flatMap(av -> av.enumValue(GeneratedValue.Type.class))
                                .orElseGet(() -> selectAutoStrategy(property));
                            if (idGeneratorType == GeneratedValue.Type.SEQUENCE) {
                                isSequence = true;
                            } else if (dialect != Dialect.MYSQL || property.getDataType() != DataType.UUID) {
                                // Property skipped
                                return;
                            }
                        }
                    }

                    if (isSequence) {
                        values.add(getSequenceStatement(unescapedTableName, property));
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

            if (definition.returningSelection() != null) {
                // TODO: proper selection of columns
                builder += RETURNING + String.join(",", resultColumns);
            }
        }
        return QueryResult.of(
            builder,
            Collections.emptyList(),
            parameterBindings,
            Collections.emptyMap()
        );
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

    private String getSequenceStatement(String unescapedTableName, PersistentProperty property) {
        final String sequenceName = resolveSequenceName(property, unescapedTableName);
        return switch (dialect) {
            case ORACLE -> quote(sequenceName) + ".nextval";
            case POSTGRES -> "nextval('" + sequenceName + "')";
            case SQL_SERVER -> "NEXT VALUE FOR " + quote(sequenceName);
            default ->
                throw new IllegalStateException("Cannot generate a sequence for dialect: " + dialect);
        };
    }

    private String resolveSequenceName(PersistentProperty identity, String unescapedTableName) {
        return identity.getAnnotationMetadata().stringValue(GeneratedValue.class, "ref")
            .map(n -> {
                if (StringUtils.isEmpty(n)) {
                    return unescapedTableName + SEQ_SUFFIX;
                } else {
                    return n;
                }
            })
            .orElseGet(() -> unescapedTableName + SEQ_SUFFIX);
    }

    @Override
    protected String getAliasName(PersistentEntity entity) {
        return entity.getAliasName();
    }

    private String getSchemaName(PersistentEntity entity) {
        return entity.getAnnotationMetadata().stringValue(MappedEntity.class, SqlMembers.SCHEMA).orElseGet(() ->
            entity.getAnnotationMetadata().stringValue(MappedEntity.class, SqlMembers.SCHEMA).orElse(null)
        );
    }

    @Override
    public String getTableName(PersistentEntity entity) {
        boolean escape = shouldEscape(entity);
        String tableName = entity.getPersistedName();
        String schema = getSchemaName(entity);
        if (StringUtils.isNotEmpty(schema)) {
            if (escape) {
                return quote(schema) + '.' + quote(tableName);
            } else {
                return schema + '.' + tableName;
            }
        } else {
            return escape ? quote(tableName) : tableName;
        }
    }

    private boolean addWriteExpression(List<String> values, PersistentProperty property) {
        DataType dt = property.getDataType();
        String transformer = getDataTransformerWriteValue(null, property).orElse(null);
        if (transformer != null) {
            return values.add(transformer);
        }
        if (dt == DataType.JSON) {
            switch (dialect) {
                case POSTGRES ->
                    values.add("to_json(" + formatParameter(values.size() + 1).name() + "::json)");
                case H2 -> values.add(formatParameter(values.size() + 1).name() + " FORMAT JSON");
                case MYSQL ->
                    values.add("CONVERT(" + formatParameter(values.size() + 1).name() + " USING UTF8MB4)");
                default -> values.add(formatParameter(values.size() + 1).name());
            }
            return true;
        }
        return values.add(formatParameter(values.size() + 1).name());
    }

    @Override
    protected void appendUpdateSetParameter(StringBuilder sb, String alias, PersistentProperty prop, Runnable appendParameter) {
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
        } else {
            super.appendUpdateSetParameter(sb, alias, prop, appendParameter);
        }
    }

    @Override
    protected void buildJoin(String joinType,
                             StringBuilder query,
                             QueryState queryState,
                             PersistentAssociationPath joinAssociation,
                             PersistentEntity associationOwner,
                             String currentJoinAlias,
                             String lastJoinAlias) {
        Association association = joinAssociation.getAssociation();
        List<Association> joinAssociationsPath = joinAssociation.getAssociations();
        PersistentEntity associatedEntity = association.getAssociatedEntity();
        final boolean escape = shouldEscape(associationOwner);
        String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
        AnnotationValue<JoinColumns> joinColumnsAnnotationValue = association.getAnnotationMetadata().getAnnotation(JoinColumns.class);
        List<AnnotationValue<JoinColumn>> joinColumnValues = joinColumnsAnnotationValue == null ? null : joinColumnsAnnotationValue.getAnnotations(VALUE_MEMBER);

        if (association.getKind() == Relation.Kind.MANY_TO_MANY || (association.isForeignKey() && StringUtils.isEmpty(mappedBy) && CollectionUtils.isEmpty(joinColumnValues))) {
            PersistentProperty identity = associatedEntity.getIdentity();
            if (identity == null) {
                throw new IllegalArgumentException("Associated entity [" + associatedEntity.getName() + "] defines no ID. Cannot join.");
            }
            final PersistentProperty associatedId = associationOwner.getIdentity();
            if (associatedId == null) {
                throw new MappingException("Cannot join on entity [" + associationOwner.getName() + "] that has no declared ID");
            }
            Optional<Association> inverseSide = association.getInverseSide().map(Function.identity());
            Association owningAssociation = inverseSide.orElse(association);
            boolean isAssociationOwner = association.getInverseSide().isEmpty();
            NamingStrategy namingStrategy = associationOwner.getNamingStrategy();
            AnnotationMetadata annotationMetadata = owningAssociation.getAnnotationMetadata();

            List<String> ownerJoinColumns = resolveJoinTableAssociatedColumns(annotationMetadata, isAssociationOwner, associationOwner, namingStrategy);
            List<String> ownerJoinTableColumns = resolveJoinTableJoinColumns(annotationMetadata, isAssociationOwner, associationOwner, namingStrategy);
            List<String> associationJoinColumns = resolveJoinTableAssociatedColumns(annotationMetadata, !isAssociationOwner, associatedEntity, namingStrategy);
            List<String> associationJoinTableColumns = resolveJoinTableJoinColumns(annotationMetadata, !isAssociationOwner, associatedEntity, namingStrategy);
            if (escape) {
                ownerJoinColumns = ownerJoinColumns.stream().map(this::quote).toList();
                ownerJoinTableColumns = ownerJoinTableColumns.stream().map(this::quote).toList();
                associationJoinColumns = associationJoinColumns.stream().map(this::quote).toList();
                associationJoinTableColumns = associationJoinTableColumns.stream().map(this::quote).toList();
            }

            String joinTableSchema = annotationMetadata
                .stringValue(ANN_JOIN_TABLE, SqlMembers.SCHEMA)
                .orElse(getSchemaName(associationOwner));
            if (StringUtils.isNotEmpty(joinTableSchema) && escape) {
                joinTableSchema = quote(joinTableSchema);
            }
            String joinTableName = annotationMetadata
                .stringValue(ANN_JOIN_TABLE, "name")
                .orElseGet(() -> getMappedName(namingStrategy, association));
            String joinTableAlias = annotationMetadata
                .stringValue(ANN_JOIN_TABLE, "alias")
                .orElseGet(() -> currentJoinAlias + joinTableName + "_");
            String finalTableName = escape ? quote(joinTableName) : joinTableName;
            if (StringUtils.isNotEmpty(joinTableSchema)) {
                finalTableName = joinTableSchema + DOT + finalTableName;
            }
            join(query,
                queryState.baseQueryDefinition(),
                joinType,
                finalTableName,
                joinTableAlias,
                lastJoinAlias,
                ownerJoinColumns,
                ownerJoinTableColumns
            );
            query.append(SPACE);
            join(query,
                queryState.baseQueryDefinition(),
                joinType,
                getTableName(associatedEntity),
                currentJoinAlias,
                joinTableAlias,
                associationJoinTableColumns,
                associationJoinColumns
            );
        } else {
            if (StringUtils.isNotEmpty(mappedBy)) {
                PersistentProperty ownerIdentity = associationOwner.getIdentity();
                if (ownerIdentity == null) {
                    throw new IllegalArgumentException("Associated entity [" + associationOwner + "] defines no ID. Cannot join.");
                }
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
                PersistentProperty associatedProperty = associatedEntity.getIdentity();
                if (associatedProperty == null) {
                    throw new IllegalArgumentException("Associated entity [" + associatedEntity.getName() + "] defines no ID. Cannot join.");
                }
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
            AnnotationValue<Annotation> joinColumnsHolder = owner.getAnnotationMetadata().getAnnotation(ANN_JOIN_COLUMNS);
            if (joinColumnsHolder != null) {
                onLeftColumns.addAll(
                    joinColumnsHolder.getAnnotations(VALUE_MEMBER)
                        .stream()
                        .flatMap(ann -> ann.stringValue(isOwner ? "name" : "referencedColumnName").stream())
                        .toList()
                );
                onRightColumns.addAll(
                    joinColumnsHolder.getAnnotations(VALUE_MEMBER)
                        .stream()
                        .flatMap(ann -> ann.stringValue(isOwner ? "referencedColumnName" : "name").stream())
                        .toList()
                );
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
        join(sb,
            queryState.baseQueryDefinition(),
            joinType,
            getTableName(associatedEntity),
            rightTableAlias,
            leftTableAlias,
            escape ? onLeftColumns.stream().map(this::quote).toList() : onLeftColumns,
            escape ? onRightColumns.stream().map(this::quote).toList() : onRightColumns
        );
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

        builder
            .append(joinType)
            .append(tableName)
            .append(SPACE)
            .append(tableAlias);
        if (queryDefinition instanceof SelectQueryDefinition selectQueryDefinition) {
            appendForUpdate(QueryPosition.AFTER_TABLE_NAME, selectQueryDefinition, builder);
        }
        builder.append(" ON ");
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

    /**
     * Quote a column name for the dialect.
     *
     * @param persistedName The persisted name.
     * @return The quoted name
     */
    @Override
    protected String quote(String persistedName) {
        return switch (dialect) {
            case MYSQL, H2 -> '`' + persistedName + '`';
            case SQL_SERVER -> '[' + persistedName + ']';
            case ORACLE ->
                // Oracle requires quoted identifiers to be in upper case
                '"' + persistedName.toUpperCase(Locale.ENGLISH) + '"';
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
        if (property.getDataType() == DataType.UUID) {
            return UUID;
        }
        if (dialect == Dialect.ORACLE) {
            return SEQUENCE;
        }
        return GeneratedValue.Type.AUTO;
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
    public QueryResult buildSelect(@NonNull AnnotationMetadata annotationMetadata, @NonNull SelectQueryDefinition definition) {
        if (definition.parametersInRole().isEmpty()) {
            // We can directly generate the query with limit and offset and omit the runtime modification
            QueryState queryState = buildQuery(annotationMetadata, definition, new QueryBuilder(), true, null);

            return QueryResult.of(
                queryState.getFinalQuery(),
                queryState.getQueryParts(),
                queryState.getParameterBindings(),
                queryState.getJoinPaths()
            );
        }

        return super.buildSelect(annotationMetadata, definition);
    }

    @Override
    protected void appendPaginationAndOrder(AnnotationMetadata annotationMetadata,
                                            SelectQueryDefinition definition,
                                            boolean pagination,
                                            QueryState queryState) {
        Map<String, Integer> parametersInRole = definition.parametersInRole();
        if (parametersInRole.isEmpty()) {
            // Directly create a query with LIMIT and ORDER
            appendOrder(annotationMetadata, definition, queryState);
            if (pagination) {
                appendLimitAndOffset(getDialect(), definition.limit(), definition.offset(), queryState.getQuery());
            }
        } else if (parametersInRole.containsKey(TypeRole.SORT) || parametersInRole.containsKey(TypeRole.PAGEABLE) || parametersInRole.containsKey(TypeRole.PAGEABLE_REQUIRED)) {
            Map.Entry<String, Integer> e = parametersInRole.entrySet().iterator().next();
            queryState.pushParameter(new QueryParameterBinding() {
                @Override
                public String getName() {
                    return e.getKey();
                }

                @Override
                public String getKey() {
                    return "";
                }

                @Override
                public int getParameterIndex() {
                    return e.getValue();
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
                    return e.getKey();
                }

                @Override
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
            PersistentProperty identity = persistentEntity.getIdentity();
            if (identity == null) {
                throw new DataAccessException("Pagination requires an entity ID on SQL Server");
            }
            orders = List.of(new PersistentPropertyOrder<>(new DefaultPersistentPropertyPath<>(identity, List.of(), null), true));
        }
        appendOrder(annotationMetadata, orders, queryState);
    }

    private static class DialectConfig {
        Boolean escapeQueries;
        String positionalFormatter;
        String positionalNameFormatter;
    }

    private static class IndexConfiguration {
        AnnotationValue<?> index;
        String tableName;
        String unquotedTableName;

        public IndexConfiguration(AnnotationValue<?> index, String tableName, String unquotedTableName) {
            this.index = index;
            this.tableName = tableName;
            this.unquotedTableName = unquotedTableName;
        }
    }

    protected class SqlSelectionVisitor extends AbstractSqlLikeQueryBuilder2.SqlSelectionVisitor {

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
            for (Iterator<PersistentProperty> iterator = properties.iterator(); iterator.hasNext(); ) {
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
                    String columnName;
                    if (computePropertyPaths()) {
                        columnName = getMappedName(namingStrategy, propertyAssociations, prop);
                    } else {
                        columnName = asPath(propertyAssociations, prop);
                    }
                    String columnAlias = getColumnAlias(prop);

                    query
                        .append(joinAlias)
                        .append(DOT)
                        .append(queryState.shouldEscape() ? quote(columnName) : columnName)
                        .append(AS_CLAUSE);
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
        public void selectAllColumns(AnnotationMetadata annotationMetadata, PersistentEntity entity, String alias) {
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
        private void selectAllColumns(StringBuilder sb, String alias) {
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
                .flatMap(SqlQueryBuilder2::flatMapEmbedded)
                .noneMatch(pp -> {
                    if (pp instanceof Association association) {
                        return !association.isForeignKey();
                    }
                    return true;
                });
        }

    }

    /**
     * The insert query definition.
     */
    public static final class InsertQueryDefinitionImpl implements InsertQueryDefinition {

        private final PersistentEntity persistentEntity;
        private final Selection<?> returningSelection;

        public InsertQueryDefinitionImpl(PersistentEntity persistentEntity) {
            this.persistentEntity = persistentEntity;
            this.returningSelection = null;
        }

        @Override
        public PersistentEntity persistentEntity() {
            return persistentEntity;
        }

        @Override
        public Selection<?> returningSelection() {
            return returningSelection;
        }
    }

}
