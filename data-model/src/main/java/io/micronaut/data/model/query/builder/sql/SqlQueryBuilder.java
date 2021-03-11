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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;

import java.lang.annotation.Annotation;
import java.sql.Blob;
import java.sql.Clob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
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
 * @since 1.0.0
 */
public class SqlQueryBuilder extends AbstractSqlLikeQueryBuilder implements QueryBuilder, SqlQueryConfiguration.DialectConfiguration {

    /**
     * The start of an IN expression.
     */
    public static final String DEFAULT_POSITIONAL_PARAMETER_MARKER = "?";
    /**
     * Annotation used to represent join tables.
     */
    private static final String ANN_JOIN_TABLE = "io.micronaut.data.jdbc.annotation.JoinTable";
    private static final String ANN_JOIN_COLUMNS = "io.micronaut.data.jdbc.annotation.JoinColumns";
    private static final String BLANK_SPACE = " ";
    private static final String SEQ_SUFFIX = "_seq";
    private static final String INSERT_INTO = "INSERT INTO ";
    private static final String JDBC_REPO_ANNOTATION = "io.micronaut.data.jdbc.annotation.JdbcRepository";
    private static final String STANDARD_FOR_UPDATE_CLAUSE = " FOR UPDATE";
    private static final String SQL_SERVER_FOR_UPDATE_CLAUSE = " WITH (UPDLOCK, ROWLOCK)";

    private final Dialect dialect;
    private final Map<Dialect, DialectConfig> perDialectConfig = new HashMap<>(3);
    private Pattern positionalParameterPattern;


    /**
     * Constructor with annotation metadata.
     * @param annotationMetadata The annotation metadata
     */
    @Creator
    public SqlQueryBuilder(AnnotationMetadata annotationMetadata) {
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
    public SqlQueryBuilder() {
        this.dialect = Dialect.ANSI;
    }

    /**
     * @param dialect The dialect
     */
    public SqlQueryBuilder(Dialect dialect) {
        ArgumentUtils.requireNonNull("dialect", dialect);
        this.dialect = dialect;
    }

    /**
     * @return The dialect being used by the builder.
     */
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
    public boolean shouldAliasProjections() {
        return false;
    }

    @Override
    protected boolean isExpandEmbedded() {
        return true;
    }

    /**
     * Builds a batch create tables statement. Designed for testing and not production usage. For production a
     *  SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entities the entities
     * @return The table
     */
    @Experimental
    public @NonNull String buildBatchCreateTableStatement(@NonNull PersistentEntity... entities) {
        return Arrays.stream(entities).flatMap(entity -> Stream.of(buildCreateTableStatements(entity)))
                .collect(Collectors.joining(System.getProperty("line.separator")));
    }

    /**
     * Builds a batch drop tables statement. Designed for testing and not production usage. For production a
     *  SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entities the entities
     * @return The table
     */
    @Experimental
    public @NonNull String buildBatchDropTableStatement(@NonNull PersistentEntity... entities) {
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
    public @NonNull String[] buildDropTableStatements(@NonNull PersistentEntity entity) {
        String tableName = getTableName(entity);
        boolean escape = shouldEscape(entity);
        String sql = "DROP TABLE " + tableName;
        Collection<Association> foreignKeyAssociations = getJoinTableAssociations(entity);
        List<String> dropStatements = new ArrayList<>();
        for (Association association : foreignKeyAssociations) {
            AnnotationMetadata associationMetadata = association.getAnnotationMetadata();
            NamingStrategy namingStrategy = entity.getNamingStrategy();
            String joinTableName = associationMetadata
                    .stringValue(ANN_JOIN_TABLE, "name")
                    .orElseGet(() ->
                            namingStrategy.mappedName(association)
                    );
            dropStatements.add("DROP TABLE " + (escape ? quote(joinTableName) : joinTableName) + ";");
        }

        dropStatements.add(sql);
        return dropStatements.toArray(new String[0]);
    }

    /**
     * Builds a join table insert statement for a given entity and association.
     * @param entity The entity
     * @param association The association
     * @return The join table insert statement
     */
    public @NonNull String buildJoinTableInsert(@NonNull PersistentEntity entity, @NonNull Association association) {
        final AnnotationMetadata associationMetadata = association.getAnnotationMetadata();
        if (!isForeignKeyWithJoinTable(association)) {
            throw new IllegalArgumentException("Join table inserts can only be built for foreign key associations that are mapped with a join table.");
        } else {
            NamingStrategy namingStrategy = entity.getNamingStrategy();
            String joinTableName = associationMetadata
                    .stringValue(ANN_JOIN_TABLE, "name")
                    .orElseGet(() ->
                            namingStrategy.mappedName(association)
                    );
            List<String> leftJoinColumns = resolveJoinTableAssociatedColumns(association, entity, namingStrategy);
            List<String> rightJoinColumns = resolveJoinTableAssociatedColumns(association, association.getAssociatedEntity(), namingStrategy);
            boolean escape = shouldEscape(entity);
            String columns = Stream.concat(leftJoinColumns.stream(), rightJoinColumns.stream())
                    .map(columnName -> escape ? quote(columnName) : columnName)
                    .collect(Collectors.joining(","));
            String placeholders = IntStream.range(0, leftJoinColumns.size() + rightJoinColumns.size()).mapToObj(i -> "?").collect(Collectors.joining(","));
            return INSERT_INTO + quote(joinTableName) + " (" + columns + ") VALUES (" + placeholders + ")";
        }
    }

    /**
     * Is the given association a foreign key reference that requires a join table.
     * @param association The association.
     * @return True if it is.
     */
    public static boolean isForeignKeyWithJoinTable(@NonNull Association association) {
        return association.isForeignKey() &&
                !association.getAnnotationMetadata()
                        .stringValue(Relation.class, "mappedBy").isPresent();
    }

    /**
     * Builds the create table statement. Designed for testing and not production usage. For production a
     * SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entity The entity
     * @return The tables for the give entity
     */
    @Experimental
    public @NonNull String[] buildCreateTableStatements(@NonNull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);
        final String unescapedTableName = getUnescapedTableName(entity);
        String tableName = getTableName(entity);
        boolean escape = shouldEscape(entity);

        PersistentProperty identity = entity.getIdentity();

        List<String> createStatements = new ArrayList<>();
        String schema = entity.getAnnotationMetadata().stringValue(MappedEntity.class, SqlMembers.SCHEMA).orElse(null);
        if (StringUtils.isNotEmpty(schema)) {
            if (escape) {
                schema = quote(schema);
            }
            createStatements.add("CREATE SCHEMA " + schema + ";");
        }

        Collection<Association> foreignKeyAssociations = getJoinTableAssociations(entity);

        NamingStrategy namingStrategy = entity.getNamingStrategy();
        if (CollectionUtils.isNotEmpty(foreignKeyAssociations)) {
            for (Association association : foreignKeyAssociations) {
                StringBuilder joinTableBuilder = new StringBuilder("CREATE TABLE ");
                PersistentEntity associatedEntity = association.getAssociatedEntity();
                String joinTableName = association.getAnnotationMetadata()
                        .stringValue(ANN_JOIN_TABLE, "name")
                        .orElseGet(() ->
                                namingStrategy.mappedName(association)
                        );
                if (escape) {
                    joinTableName = quote(joinTableName);
                }
                joinTableBuilder.append(joinTableName).append(" (");
                traversePersistentProperties(entity.getIdentity(), (associations, property) -> {
                    String columnName =  namingStrategy.mappedJoinTableColumn(entity, associations, property);
                    if (escape) {
                        columnName = quote(columnName);
                    }
                    joinTableBuilder
                            .append(addTypeToColumn(property, columnName, true))
                            .append(',');
                });
                traversePersistentProperties(associatedEntity.getIdentity(), (associations, property) -> {
                    String columnName =  namingStrategy.mappedJoinTableColumn(associatedEntity, associations, property);
                    if (escape) {
                        columnName = quote(columnName);
                    }
                    joinTableBuilder
                            .append(addTypeToColumn(property, columnName, true))
                            .append(',');
                });
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

        if (identity != null) {
            int[] idsCount = {0};
            traversePersistentProperties(identity, (associations, property) -> {
                idsCount[0]++;
            });
            if (idsCount[0] > 1) {
                generatePkAfterColumns = true;
            }
            boolean finalGeneratePkAfterColumns = generatePkAfterColumns;
            traversePersistentProperties(identity, (associations, property) -> {
                String column = namingStrategy.mappedName(associations, property);
                if (escape) {
                    column = quote(column);
                }
                primaryColumnsName.add(column);

                column = addTypeToColumn(property, column, isRequired(associations, property));
                if (isNotForeign(associations)) {
                    column = addGeneratedStatementToColumn(property, column, !finalGeneratePkAfterColumns);
                }
                columns.add(column);
            });
        }

        BiConsumer<List<Association>, PersistentProperty> addColumn = (associations, property) -> {
            String column = namingStrategy.mappedName(associations, property);
            if (escape) {
                column = quote(column);
            }
            column = addTypeToColumn(property, column, isRequired(associations, property));
            if (isNotForeign(associations)) {
                column = addGeneratedStatementToColumn(property, column, false);
            }
            columns.add(column);
        };

        for (PersistentProperty prop : entity.getPersistentProperties()) {
            traversePersistentProperties(prop, addColumn);
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

        if (identity != null && identity.isGenerated()) {
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
                    createSequenceStmt += " NOCACHE NOCYCLE";
                } else {
                    if (isSqlServer) {
                        createSequenceStmt += " INCREMENT BY 1";
                    }
                }
                createStatements.add(createSequenceStmt);
            }
        }
        createStatements.add(builder.toString());
        return createStatements.toArray(new String[0]);
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
                    // for Oracle we use sequences so just add NOT NULL
                    // then alter the table for sequences
                    if (type == UUID) {
                        column += " NOT NULL DEFAULT SYS_GUID()";
                    } else if (type == IDENTITY) {
                        if (isPk) {
                            column += " GENERATED ALWAYS AS IDENTITY";
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
    private List<String> resolveJoinTableJoinColumns(Association association, PersistentEntity entity, NamingStrategy namingStrategy) {
        List<String> joinColumns = getJoinedColumns(association, "name");
        if (!joinColumns.isEmpty()) {
            return joinColumns;
        }
        List<String> columns = new ArrayList<>();
        traversePersistentProperties(entity.getIdentity(), (associations, property) -> {
            columns.add(namingStrategy.mappedName(associations, property));
        });
        return columns;
    }

    @NonNull
    private List<String> resolveJoinTableAssociatedColumns(Association association, PersistentEntity entity, NamingStrategy namingStrategy) {
        List<String> joinColumns = getJoinedColumns(association, "referencedColumnName");
        if (!joinColumns.isEmpty()) {
            return joinColumns;
        }
        PersistentProperty identity = entity.getIdentity();
        if (identity == null) {
            throw new MappingException("Cannot have a foreign key association without an ID on entity: " + entity.getName());
        }
        List<String> columns = new ArrayList<>();
        traversePersistentProperties(identity, (associations, property) -> {
            String columnName = namingStrategy.mappedJoinTableColumn(entity, associations, property);
            columns.add(columnName);
        });
        return columns;
    }

    @NonNull
    private List<String> getJoinedColumns(Association association, String columnType) {
        Optional<Association> inverse = association.getInverseSide().map(Function.identity());
        boolean isOwner = !inverse.isPresent();
        AnnotationValue<Annotation> joinTable = inverse.orElse(association).getAnnotationMetadata().getAnnotation(ANN_JOIN_TABLE);
        if (joinTable != null) {
            return joinTable.getAnnotations(isOwner ? "joinColumns" : "inverseJoinColumns")
                    .stream()
                    .map(ann -> ann.stringValue(columnType).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @NonNull
    private Collection<Association> getJoinTableAssociations(PersistentEntity persistentEntity) {
        return Stream.concat(Stream.of(persistentEntity.getIdentity()), persistentEntity.getPersistentProperties().stream())
                .flatMap(this::flatMapEmbedded)
                .filter(p -> {
                if (p instanceof Association) {
                    Association a = (Association) p;
                    return isForeignKeyWithJoinTable(a);
                }
                return false;
            }).map(p -> (Association) p).collect(Collectors.toList());
    }

    @Override
    protected void selectAllColumns(QueryState queryState, StringBuilder queryBuffer) {
        PersistentEntity entity = queryState.getEntity();
        String alias = queryState.getCurrentAlias();
        selectAllColumns(entity, alias, queryBuffer);

        QueryModel queryModel = queryState.getQueryModel();

        Collection<JoinPath> allPaths = queryModel.getJoinPaths();
        if (CollectionUtils.isNotEmpty(allPaths)) {

            Collection<JoinPath> joinPaths = allPaths.stream().filter(jp -> {
                Join.Type jt = jp.getJoinType();
                return jt.name().contains("FETCH");
            }).collect(Collectors.toList());

            if (CollectionUtils.isNotEmpty(joinPaths)) {
                for (JoinPath joinPath : joinPaths) {
                    Association association = joinPath.getAssociation();
                    if (association instanceof Embedded) {
                        // joins on embedded don't make sense
                        continue;
                    }

                    PersistentEntity associatedEntity = association.getAssociatedEntity();
                    NamingStrategy namingStrategy = associatedEntity.getNamingStrategy();

                    String aliasName = getAliasName(joinPath);
                    String joinPathAlias = getPathOnlyAliasName(joinPath);

                    queryBuffer.append(COMMA);

                    boolean includeIdentity = false;
                    if (association.isForeignKey()) {
                        // in the case of a foreign key association the ID is not in the table
                        // so we need to retrieve it
                        includeIdentity = true;
                    }
                    traversePersistentProperties(associatedEntity, includeIdentity, (propertyAssociations, prop) ->  {
                        String columnName;
                        if (computePropertyPaths()) {
                            columnName = namingStrategy.mappedName(propertyAssociations, prop);
                        } else {
                            columnName = asPath(propertyAssociations, prop);
                        }
                        queryBuffer
                                .append(aliasName)
                                .append(DOT)
                                .append(queryState.shouldEscape() ? quote(columnName) : columnName)
                                .append(AS_CLAUSE)
                                .append(joinPathAlias)
                                .append(columnName)
                                .append(COMMA);
                    });
                    queryBuffer.setLength(queryBuffer.length() - 1);
                }
            }
        }
    }

    /**
     * Selects all columns for the given entity and alias.
     * @param entity The entity
     * @param alias The alias
     * @param sb The builder to add the columns
     */
    @Override
    public void selectAllColumns(PersistentEntity entity, String alias, StringBuilder sb) {
        if (canUseWildcardForSelect(entity)) {
            if (alias != null) {
                sb.append(alias).append(DOT);
            }
            sb.append("*");
            return;
        }
        boolean escape = shouldEscape(entity);
        NamingStrategy namingStrategy = entity.getNamingStrategy();
        int length = sb.length();
        traversePersistentProperties(entity, (associations, property) -> {
            String transformed = getDataTransformerReadValue(alias, property).orElse(null);
            if (transformed != null) {
                sb.append(transformed).append(AS_CLAUSE).append(property.getPersistedName());
            } else {
                String column = namingStrategy.mappedName(associations, property);
                if (escape) {
                    column = quote(column);
                }
                sb.append(alias).append(DOT).append(column);
            }
            sb.append(COMMA);
        });
        int newLength = sb.length();
        if (newLength == length) {
            if (alias != null) {
                sb.append(alias).append(DOT);
            }
            sb.append("*");
        } else {
            sb.setLength(newLength - 1);
        }
    }

    private boolean canUseWildcardForSelect(PersistentEntity entity) {
        return Stream.concat(Stream.of(entity.getIdentity()), entity.getPersistentProperties().stream())
                .flatMap(this::flatMapEmbedded)
                .noneMatch(pp -> {
                    if (pp instanceof Association) {
                        Association association = (Association) pp;
                        return !association.isForeignKey();
                    }
                    return true;
                });
    }

    private Stream<? extends PersistentProperty> flatMapEmbedded(PersistentProperty pp) {
        if (pp instanceof Embedded) {
            Embedded embedded = (Embedded) pp;
            PersistentEntity embeddedEntity = embedded.getAssociatedEntity();
            return embeddedEntity.getPersistentProperties()
                    .stream()
                    .flatMap(this::flatMapEmbedded);
        }
        return Stream.of(pp);
    }

    @Override
    public String resolveJoinType(Join.Type jt) {
        String joinType;
        switch (jt) {
            case LEFT:
            case LEFT_FETCH:
                joinType = " LEFT JOIN ";
                break;
            case RIGHT:
            case RIGHT_FETCH:
                joinType = " RIGHT JOIN ";
                break;
            case OUTER:
                joinType = " FULL OUTER JOIN ";
                break;
            default:
                joinType = " INNER JOIN ";
        }
        return joinType;
    }

    @NonNull
    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, PersistentEntity entity) {
        boolean escape = shouldEscape(entity);
        final String unescapedTableName = getUnescapedTableName(entity);

        NamingStrategy namingStrategy = entity.getNamingStrategy();

        Collection<? extends PersistentProperty> persistentProperties = entity.getPersistentProperties();
        Map<String, String> parameters = new LinkedHashMap<>(persistentProperties.size());
        Map<String, DataType> parameterTypes = new LinkedHashMap<>(persistentProperties.size());

        List<String> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();

        for (PersistentProperty prop : persistentProperties) {
            if (!prop.isGenerated()) {
                traversePersistentProperties(prop, (associations, property) -> {
                    String path = asPath(associations, property);
                    parameterTypes.put(prop.getName(), prop.getDataType());
                    addWriteExpression(values, prop);
                    parameters.put(String.valueOf(values.size()), path);

                    String columnName = namingStrategy.mappedName(associations, property);
                    if (escape) {
                        columnName = quote(columnName);
                    }
                    columns.add(columnName);
                });
            }
        }

        PersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            traversePersistentProperties(identity, (associations, property) -> {
                boolean isSequence = false;
                if (isNotForeign(associations)) {
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
                    String path = asPath(associations, property);
                    parameterTypes.put(path, property.getDataType());
                    addWriteExpression(values, property);
                    parameters.put(String.valueOf(values.size()), path);
                }

                String columnName = namingStrategy.mappedName(associations, property);
                if (escape) {
                    columnName = quote(columnName);
                }
                columns.add(columnName);
            });
        }

        String builder = INSERT_INTO + getTableName(entity) +
                " (" + String.join(",", columns) + CLOSE_BRACKET + " " +
                "VALUES (" + String.join(String.valueOf(COMMA), values) + CLOSE_BRACKET;
        return QueryResult.of(
                builder,
                parameters,
                parameterTypes,
                Collections.emptySet()
        );
    }

    private String getSequenceStatement(String unescapedTableName, PersistentProperty property) {
        final String sequenceName = resolveSequenceName(property, unescapedTableName);
        switch (dialect) {
            case ORACLE:
                return quote(sequenceName) + ".nextval";
            case POSTGRES:
                return "nextval('" + sequenceName + "')";
            case SQL_SERVER:
                return "NEXT VALUE FOR " + quote(sequenceName);
            default:
                throw new IllegalStateException("Cannot generate a sequence for dialect: " + dialect);
        }
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

    @NonNull
    @Override
    public QueryResult buildPagination(@NonNull Pageable pageable) {
        int size = pageable.getSize();
        if (size > 0) {
            StringBuilder builder = new StringBuilder(" ");
            long from = pageable.getOffset();
            switch (dialect) {
                case H2:
                case MYSQL:
                    if (from == 0) {
                        builder.append("LIMIT ").append(size);
                    } else {
                        builder.append("LIMIT ").append(from).append(',').append(size);
                    }
                    break;
                case POSTGRES:
                    builder.append("LIMIT ").append(size).append(" ");
                    if (from != 0) {
                        builder.append("OFFSET ").append(from);
                    }
                    break;

                case SQL_SERVER:
                    // SQL server requires OFFSET always
                    if (from == 0) {
                        builder.append("OFFSET ").append(0).append(" ROWS ");
                    }
                    // intentional fall through
                case ANSI:
                case ORACLE:
                default:
                    if (from != 0) {
                        builder.append("OFFSET ").append(from).append(" ROWS ");
                    }
                    builder.append("FETCH NEXT ").append(size).append(" ROWS ONLY ");
                    break;
            }
            return QueryResult.of(
                    builder.toString(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptySet()
            );
        } else {
            return QueryResult.of(
                    "",
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptySet()
            );
        }
    }

    @Override
    protected String getAliasName(PersistentEntity entity) {
        return entity.getAliasName();
    }

    @Override
    public String getTableName(PersistentEntity entity) {
        boolean escape = shouldEscape(entity);
        String tableName = entity.getPersistedName();
        String schema = entity.getAnnotationMetadata().stringValue(MappedEntity.class, SqlMembers.SCHEMA).orElse(null);
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

    @Override
    protected String formatStartsWith() {
        if (dialect == Dialect.ORACLE) {
            return " LIKE '%' || ";
        } else {
            return super.formatStartsWith();
        }
    }

    @Override
    protected String formEndsWithEnd() {
        if (dialect == Dialect.ORACLE) {
            return " ";
        } else {
            return super.formEndsWithEnd();
        }
    }

    @Override
    protected String formatEndsWith() {
        if (dialect == Dialect.ORACLE) {
            return " || '%'";
        } else {
            return super.formatEndsWith();
        }
    }

    @Override
    protected String formatStartsWithBeginning() {
        if (dialect == Dialect.ORACLE) {
            return " LIKE ";
        } else {
            return super.formatStartsWithBeginning();
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
                case POSTGRES:
                    return values.add("to_json(" + formatParameter(values.size() + 1).getName() + "::json)");
                case H2:
                    return values.add(formatParameter(values.size() + 1).getName() + " FORMAT JSON");
                case MYSQL:
                    return values.add("CONVERT(" + formatParameter(values.size() + 1).getName() + " USING UTF8MB4)");
                default:
                    return values.add(formatParameter(values.size() + 1).getName());
            }
        }
        return values.add(formatParameter(values.size() + 1).getName());
    }

    @Override
    protected void appendUpdateSetParameter(StringBuilder sb, String alias, PersistentProperty prop, Placeholder param) {
        String transformed = getDataTransformerWriteValue(alias, prop).orElse(null);
        if (transformed != null) {
            sb.append(transformed);
            return;
        }
        if (prop.getDataType() == DataType.JSON) {
            switch (dialect) {
                case H2:
                    sb.append(param.getName()).append(" FORMAT JSON");
                    break;
                case MYSQL:
                    sb.append("CONVERT(").append(param.getName()).append(" USING UTF8MB4)");
                    break;
                case POSTGRES:
                    sb.append("to_json(").append(param.getName()).append("::json)");
                    break;
                default:
                    super.appendUpdateSetParameter(sb, alias, prop, param);
            }
        } else {
            super.appendUpdateSetParameter(sb, alias, prop, param);
        }
    }

    @Override
    protected String[] buildJoin(
            final String alias,
            JoinPath joinPath,
            String joinType,
            StringBuilder target,
            Map<String, String> appliedJoinPaths,
            QueryState queryState) {
        Association[] associationPath = joinPath.getAssociationPath();
        String[] joinAliases;
        if (ArrayUtils.isEmpty(associationPath)) {
            throw new IllegalArgumentException("Invalid association path [" + joinPath.getPath() + "]");
        }

        List<Association> joinAssociationsPath = new ArrayList<>(associationPath.length);

        joinAliases = new String[associationPath.length];
        StringJoiner pathSoFar = new StringJoiner(".");
        String joinAlias = alias;
        for (int i = 0; i < associationPath.length; i++) {
            Association association = associationPath[i];
            pathSoFar.add(association.getName());
            if (association instanceof Embedded) {
                joinAssociationsPath.add(association);
                continue;
            }

            String currentPath = pathSoFar.toString();
            String existingAlias = appliedJoinPaths.get(alias + DOT + currentPath);
            if (existingAlias != null) {
                joinAliases[i] = existingAlias;
                joinAlias = existingAlias;
            } else {
                PersistentEntity associatedEntity = association.getAssociatedEntity();
                int finalI = i;
                JoinPath joinPathToUse = queryState.getQueryModel().getJoinPath(currentPath)
                          .orElseGet(() ->
                                  new JoinPath(
                                          currentPath,
                                          Arrays.copyOfRange(associationPath, 0, finalI + 1),
                                          joinPath.getJoinType(),
                                          joinPath.getAlias().orElse(null))
                          );
                joinAliases[i] = getAliasName(joinPathToUse);
                final PersistentEntity associationOwner = association.getOwner();
                final boolean escape = shouldEscape(associationOwner);
                String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);

                String currentJoinAlias = joinAliases[i];
                if (association.isForeignKey() && StringUtils.isEmpty(mappedBy)) {
                    PersistentProperty identity = associatedEntity.getIdentity();
                    if (identity == null) {
                        throw new IllegalArgumentException("Associated entity [" + associatedEntity.getName() + "] defines no ID. Cannot join.");
                    }
                    final PersistentProperty associatedId = associationOwner.getIdentity();
                    if (associatedId == null) {
                        throw new MappingException("Cannot join on entity [" + associationOwner.getName() + "] that has no declared ID");
                    }
                    NamingStrategy namingStrategy = associationOwner.getNamingStrategy();
                    List<String> ownerJoinColumns = resolveJoinTableJoinColumns(association, associationOwner, namingStrategy);
                    List<String> ownerJoinTableColumns = resolveJoinTableAssociatedColumns(association, associationOwner, namingStrategy);
                    List<String> associationJoinColumns = resolveJoinTableJoinColumns(association, associatedEntity, namingStrategy);
                    List<String> associationJoinTableColumns = resolveJoinTableAssociatedColumns(association, associatedEntity, namingStrategy);
                    if (escape) {
                        ownerJoinColumns = ownerJoinColumns.stream().map(this::quote).collect(Collectors.toList());
                        ownerJoinTableColumns = ownerJoinTableColumns.stream().map(this::quote).collect(Collectors.toList());
                        associationJoinColumns = associationJoinColumns.stream().map(this::quote).collect(Collectors.toList());
                        associationJoinTableColumns = associationJoinTableColumns.stream().map(this::quote).collect(Collectors.toList());
                    }
                    String joinTableName = association.getAnnotationMetadata()
                            .stringValue(ANN_JOIN_TABLE, "name")
                            .orElseGet(() -> namingStrategy.mappedName(association));
                    String joinTableAlias = association.getAnnotationMetadata()
                            .stringValue(ANN_JOIN_TABLE, "alias")
                            .orElseGet(() -> currentJoinAlias + joinTableName + "_");
                    join(target,
                            queryState.getQueryModel(),
                            joinType,
                            escape ? quote(joinTableName) : joinTableName,
                            joinTableAlias,
                            joinAlias,
                            ownerJoinColumns,
                            ownerJoinTableColumns
                    );
                    target.append(SPACE);
                    join(target,
                            queryState.getQueryModel(),
                            joinType,
                            getTableName(associatedEntity),
                            currentJoinAlias,
                            joinTableAlias,
                            associationJoinTableColumns,
                            associationJoinColumns
                    );
                } else {
                    if (StringUtils.isNotEmpty(mappedBy)) {
                        PersistentEntity associatedEntityOwner = findOwner(joinAssociationsPath, association).orElseGet(queryState::getEntity);
                        PersistentProperty ownerIdentity = associatedEntityOwner.getIdentity();
                        if (ownerIdentity == null) {
                            throw new IllegalArgumentException("Associated entity [" + associatedEntityOwner + "] defines no ID. Cannot join.");
                        }
                        PersistentProperty associatedProperty = associatedEntity.getPropertyByName(mappedBy);
                        if (associatedProperty == null) {
                            throw new MappingException("Foreign key association with mappedBy references a property that doesn't exist [" + mappedBy + "] of entity: " + associatedEntity.getName());
                        }
                        join(target,
                                joinType,
                                queryState,
                                associatedEntity,
                                associationOwner,
                                joinAlias,
                                currentJoinAlias,
                                joinAssociationsPath,
                                ownerIdentity,
                                Collections.emptyList(),
                                associatedProperty);
                    } else {
                        PersistentProperty associatedProperty = association.getAssociatedEntity().getIdentity();
                        if (associatedProperty == null) {
                            throw new IllegalArgumentException("Associated entity [" + association.getOwner().getName() + "] defines no ID. Cannot join.");
                        }
                        join(target,
                                joinType,
                                queryState,
                                associatedEntity,
                                associationOwner,
                                joinAlias,
                                currentJoinAlias,
                                joinAssociationsPath,
                                association,
                                Collections.emptyList(),
                                associatedProperty);
                    }
                }
                joinAlias = currentJoinAlias;
            }
            joinAssociationsPath.clear();
        }
        return joinAliases;
    }

    private void join(StringBuilder sb,
                      String joinType,
                      QueryState queryState,
                      PersistentEntity associatedEntity,
                      PersistentEntity associationOwner,
                      String leftTableAlias,
                      String rightTableAlias,
                      List<Association> leftPropertyAssociations,
                      PersistentProperty leftProperty,
                      List<Association> rightPropertyAssociations,
                      PersistentProperty rightProperty) {

        if (!computePropertyPaths()) {
            join(sb,
                    queryState.getQueryModel(),
                    joinType,
                    getTableName(associatedEntity),
                    leftTableAlias,
                    rightTableAlias,
                    asPath(leftPropertyAssociations, leftProperty),
                    asPath(rightPropertyAssociations, rightProperty)
            );
            return;
        }
        final boolean escape = shouldEscape(associationOwner);
        List<String> onLeftColumns = new ArrayList<>();
        List<String> onRightColumns = new ArrayList<>();

        Association association = null;
        if (leftProperty instanceof Association) {
            association = (Association) leftProperty;
        } else if (rightProperty instanceof Association) {
            association = (Association) rightProperty;
        }
        if (association != null) {
            Optional<Association> inverse = association.getInverseSide().map(Function.identity());
            Association owner = inverse.orElse(association);
            boolean isOwner = leftProperty == owner;
            AnnotationValue<Annotation> joinColumnsHolder = owner.getAnnotationMetadata().getAnnotation(ANN_JOIN_COLUMNS);
            if (joinColumnsHolder != null) {
                onLeftColumns.addAll(
                        joinColumnsHolder.getAnnotations("value")
                        .stream()
                        .map(ann -> ann.stringValue(isOwner ? "name" : "referencedColumnName").orElse(null))
                        .collect(Collectors.toList())
                );
                onRightColumns.addAll(
                        joinColumnsHolder.getAnnotations("value")
                            .stream()
                            .map(ann -> ann.stringValue(isOwner ? "referencedColumnName" : "name").orElse(null))
                            .collect(Collectors.toList())
                );
            }
        }
        if (onLeftColumns.isEmpty()) {
            traversePersistentProperties(leftProperty, (associations, p) -> {
                String column = leftProperty.getOwner().getNamingStrategy().mappedName(merge(leftPropertyAssociations, associations), p);
                onLeftColumns.add(column);
            });
            if (onLeftColumns.isEmpty()) {
                throw new MappingException("Cannot join on entity [" + leftProperty.getOwner().getName() + "] that has no declared ID");
            }
        }
        if (onRightColumns.isEmpty()) {
            traversePersistentProperties(rightProperty, (associations, p) -> {
                String column = rightProperty.getOwner().getNamingStrategy().mappedName(merge(rightPropertyAssociations, associations), p);
                onRightColumns.add(column);
            });
        }
        join(sb,
                queryState.getQueryModel(),
                joinType,
                getTableName(associatedEntity),
                rightTableAlias,
                leftTableAlias,
                escape ? onLeftColumns.stream().map(this::quote).collect(Collectors.toList()) : onLeftColumns,
                escape ? onRightColumns.stream().map(this::quote).collect(Collectors.toList()) : onRightColumns
        );
    }

    private Optional<PersistentEntity> findOwner(List<Association> associations, PersistentProperty property) {
        PersistentEntity owner = property.getOwner();
        if (!owner.isEmbeddable()) {
            return Optional.of(owner);
        }
        ListIterator<Association> listIterator = associations.listIterator(associations.size());
        while (listIterator.hasPrevious()) {
            Association association = listIterator.previous();
            if (association.getKind() != Relation.Kind.EMBEDDED) {
                return Optional.of(association.getOwner());
            }
        }
        return Optional.empty();
    }

    private void join(StringBuilder sb,
                        QueryModel queryModel,
                        String joinType,
                        String tableName,
                        String tableAlias,
                        String onTableName,
                        String onTableColumn,
                        String tableColumnName) {
        sb
            .append(joinType)
            .append(tableName)
            .append(SPACE)
            .append(tableAlias);
        appendForUpdate(QueryPosition.AFTER_TABLE_NAME, queryModel, sb);
        sb
            .append(" ON ")
            .append(onTableName)
            .append(DOT)
            .append(onTableColumn)
            .append('=')
            .append(tableAlias)
            .append(DOT)
            .append(tableColumnName);
    }

    private void join(StringBuilder builder,
                      QueryModel queryModel,
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
        appendForUpdate(QueryPosition.AFTER_TABLE_NAME, queryModel, builder);
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
     * @param persistedName The persisted name.
     * @return The quoted name
     */
    @Override
    protected String quote(String persistedName) {
        switch (dialect) {
            case MYSQL:
            case H2:
                return '`' + persistedName + '`';
            case SQL_SERVER:
                return '[' + persistedName + ']';
            case ORACLE:
                // Oracle requires quoted identifiers to be in upper case
                return '"' + persistedName.toUpperCase(Locale.ENGLISH) + '"';
            default:
                return '"' + persistedName + '"';
        }
    }

    @Override
    public String getColumnName(PersistentProperty persistentProperty) {
        return persistentProperty.getPersistedName();
    }

    @Override
    protected void appendProjectionRowCount(StringBuilder queryString, String logicalName) {
        queryString.append(FUNCTION_COUNT)
                .append(OPEN_BRACKET)
                .append('*')
                .append(CLOSE_BRACKET);
    }

    @Override
    protected void appendForUpdate(QueryPosition queryPosition, QueryModel query, StringBuilder queryBuilder) {
        if (query.isForUpdate()) {
            boolean isSqlServer = Dialect.SQL_SERVER.equals(dialect);
            if (isSqlServer && queryPosition.equals(QueryPosition.AFTER_TABLE_NAME) ||
                !isSqlServer && queryPosition.equals(QueryPosition.END_OF_QUERY)) {
                queryBuilder.append(isSqlServer ? SQL_SERVER_FOR_UPDATE_CLAUSE : STANDARD_FOR_UPDATE_CLAUSE);
            }
        }
    }

    @Override
    protected final boolean computePropertyPaths() {
        return true;
    }

    @Override
    protected boolean isAliasForBatch() {
        return false;
    }

    @Override
    public Placeholder formatParameter(int index) {
        DialectConfig dialectConfig = perDialectConfig.get(dialect);
        if (dialectConfig != null && dialectConfig.positionalFormatter != null) {
            return new Placeholder(
                    String.format(dialectConfig.positionalFormatter, index),
                    String.valueOf(index)
            );
        } else {
            return new Placeholder("?", String.valueOf(index));
        }
    }

    /**
     * Selects the default fallback strategy. For a generated value.
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

    private String addTypeToColumn(PersistentProperty prop, String column, boolean required) {
        if (prop instanceof Association) {
            throw new IllegalStateException("Association is not supported here");
        }
        AnnotationMetadata annotationMetadata = prop.getAnnotationMetadata();
        String definition = annotationMetadata.stringValue(MappedProperty.class, "definition").orElse(null);
        DataType dataType = prop.getDataType();
        if (definition != null) {
            return column + " " + definition;
        }
        OptionalInt precision = annotationMetadata.intValue("javax.persistence.Column", "precision");
        OptionalInt scale = annotationMetadata.intValue("javax.persistence.Column", "scale");

        switch (dataType) {
            case STRING:
                int stringLength = annotationMetadata.findAnnotation("javax.validation.constraints.Size$List")
                        .flatMap(v -> {
                            Optional value = v.getValue(AnnotationValue.class);
                            return (Optional<AnnotationValue<Annotation>>) value;
                        }).map(v -> v.intValue("max"))
                        .orElseGet(() -> annotationMetadata.intValue("javax.persistence.Column", "length"))
                        .orElse(255);

                column += " VARCHAR(" + stringLength + ")";
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case UUID:
                if (dialect == Dialect.ORACLE || dialect == Dialect.MYSQL) {
                    column += " VARCHAR(36)";
                } else if (dialect == Dialect.SQL_SERVER) {
                    column += " UNIQUEIDENTIFIER";
                } else {
                    column += " UUID";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case BOOLEAN:
                if (dialect == Dialect.ORACLE) {
                    column += " NUMBER(3)";
                } else if (dialect == Dialect.SQL_SERVER) {
                    column += " BIT NOT NULL";
                } else {
                    column += " BOOLEAN";
                    if (required) {
                        column += " NOT NULL";
                    }
                }
                break;
            case TIMESTAMP:
                if (dialect == Dialect.ORACLE) {
                    column += " TIMESTAMP";
                    if (required) {
                        column += " NOT NULL";
                    }
                } else if (dialect == Dialect.SQL_SERVER) {
                    // sql server timestamp is an internal type, use datetime instead
                    column += " DATETIME2";
                    if (required) {
                        column += " NOT NULL";
                    }
                } else if (dialect == Dialect.MYSQL) {
                    // mysql doesn't allow timestamp without default
                    column += " TIMESTAMP(6) DEFAULT NOW(6)";
                } else {
                    column += " TIMESTAMP";
                    if (required) {
                        column += " NOT NULL";
                    }
                }
                break;
            case DATE:
                column += " DATE";
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case LONG:
                if (dialect == Dialect.ORACLE) {
                    column += " NUMBER(19)";
                } else {
                    column += " BIGINT";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case CHARACTER:
                if (dialect == Dialect.ORACLE) {
                    column += " NUMBER(10)";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " INTEGER";
                } else {
                    column += " INT";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case INTEGER:
                if (precision.isPresent()) {
                    String numericName = dialect == Dialect.ORACLE ? "NUMBER" : "NUMERIC";
                    column += " " + numericName + "(" + precision.getAsInt() + ")";
                } else if (dialect == Dialect.ORACLE) {
                    column += " NUMBER(10)";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " INTEGER";
                } else {
                    column += " INT";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case BIGDECIMAL:
                if (precision.isPresent()) {
                    if (scale.isPresent()) {
                        String numericName = dialect == Dialect.ORACLE ? "NUMBER" : "NUMERIC";
                        column += " " + numericName + "(" + precision.getAsInt() + "," + scale.getAsInt() + ")";
                    } else {
                        column += " FLOAT(" + precision.getAsInt() + ")";
                    }
                } else if (dialect == Dialect.ORACLE) {
                    column += " FLOAT(126)";
                } else {
                    column += " DECIMAL";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case FLOAT:
                if (precision.isPresent()) {
                    if (scale.isPresent()) {
                        String numericName = dialect == Dialect.ORACLE ? "NUMBER" : "NUMERIC";
                        column += " " + numericName + "(" + precision.getAsInt() + "," + scale.getAsInt() + ")";
                    } else {
                        column += " FLOAT(" + precision.getAsInt() + ")";
                    }
                } else if (dialect == Dialect.ORACLE || dialect == Dialect.SQL_SERVER) {
                    column += " FLOAT(53)";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " REAL";
                } else {
                    column += " FLOAT";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case BYTE_ARRAY:
                if (dialect == Dialect.POSTGRES) {
                    column += " BYTEA";
                } else if (dialect == Dialect.SQL_SERVER) {
                    column += " VARBINARY(MAX)";
                } else if (dialect == Dialect.ORACLE) {
                    column += " BLOB";
                } else {
                    column += " BLOB";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case DOUBLE:
                if (precision.isPresent()) {
                    if (scale.isPresent()) {
                        String numericName = dialect == Dialect.ORACLE ? "NUMBER" : "NUMERIC";
                        column += " " + numericName + "(" + precision.getAsInt() + "," + scale.getAsInt() + ")";
                    } else {
                        column += " FLOAT(" + precision.getAsInt() + ")";
                    }
                } else if (dialect == Dialect.ORACLE) {
                    column += " FLOAT(23)";
                } else if (dialect == Dialect.MYSQL || dialect == Dialect.H2) {
                    column += " DOUBLE";
                } else {
                    column += " DOUBLE PRECISION";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case SHORT:
            case BYTE:
                if (dialect == Dialect.ORACLE) {
                    column += " NUMBER(5)";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " SMALLINT";
                } else {
                    column += " TINYINT";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case JSON:
                switch (dialect) {
                    case POSTGRES:
                        column += " JSONB";
                        break;
                    case SQL_SERVER:
                        column += " NVARCHAR(MAX)";
                        break;
                    case ORACLE:
                        column += " CLOB";
                        break;
                    default:
                        column += " JSON";
                        break;
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case STRING_ARRAY:
            case CHARACTER_ARRAY:
                if (dialect == Dialect.H2) {
                    column += " ARRAY";
                } else {
                    column += " VARCHAR(255) ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case SHORT_ARRAY:
                if (dialect == Dialect.H2) {
                    column += " ARRAY";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " SMALLINT ARRAY";
                } else {
                    column += " TINYINT ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case INTEGER_ARRAY:
                if (dialect == Dialect.H2) {
                    column += " ARRAY";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " INTEGER ARRAY";
                } else {
                    column += " INT ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case LONG_ARRAY:
                if (dialect == Dialect.H2) {
                    column += " ARRAY";
                } else {
                    column += " BIGINT ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case FLOAT_ARRAY:
                if (dialect == Dialect.H2) {
                    column += " ARRAY";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " REAL ARRAY";
                } else {
                    column += " FLOAT ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case DOUBLE_ARRAY:
                if (dialect == Dialect.H2) {
                    column += " ARRAY";
                } else if (dialect == Dialect.POSTGRES) {
                    column += " DOUBLE PRECISION ARRAY";
                } else {
                    column += " DOUBLE ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case BOOLEAN_ARRAY:
                if (dialect == Dialect.H2) {
                    column += " ARRAY";
                } else {
                    column += " BOOLEAN ARRAY";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            default:
                if (prop.isEnum()) {
                    column += " VARCHAR(255)";
                    if (required) {
                        column += " NOT NULL";
                    }
                    break;
                } else if (prop.isAssignable(Clob.class)) {
                    if (dialect == Dialect.POSTGRES) {
                        column += " TEXT";
                    } else {
                        column += " CLOB";
                    }
                    if (required) {
                        column += " NOT NULL";
                    }
                    break;
                } else if (prop.isAssignable(Blob.class)) {
                    if (dialect == Dialect.POSTGRES) {
                        column += " BYTEA";
                    } else {
                        column += " BLOB";
                    }
                    if (required) {
                        column += " NOT NULL";
                    }
                    break;
                } else {
                    throw new MappingException("Unable to create table column for property [" + prop.getName() + "] of entity [" + prop.getOwner().getName() + "] with unknown data type: " + dataType);
                }
        }
        return column;
    }

    @Override
    public boolean supportsForUpdate() {
        return true;
    }

    @Override
    public Dialect dialect() {
        return dialect;
    }

    @Override
    public String positionalParameterFormat() {
        DialectConfig dialectConfig = perDialectConfig.get(dialect);
        if (dialectConfig != null && dialectConfig.positionalFormatter != null) {
            return dialectConfig.positionalFormatter;
        }
        return DEFAULT_POSITIONAL_PARAMETER_MARKER;
    }

    /**
     * @return The regex pattern for positional parameters.
     */
    public Pattern positionalParameterPattern() {
        if (this.positionalParameterPattern == null) {
            String positionalParameterFormat = positionalParameterFormat();
            boolean messageFormat = positionalParameterFormat.endsWith("%s");
            if (messageFormat) {
                String pattern = positionalParameterFormat.substring(0, positionalParameterFormat.length() - 2);
                pattern = java.util.regex.Pattern.quote(pattern) + "\\d";
                this.positionalParameterPattern = Pattern.compile(pattern);
            } else {
                this.positionalParameterPattern = Pattern.compile(Pattern.quote(positionalParameterFormat));
            }
        }
        return this.positionalParameterPattern;
    }

    @Override
    public boolean escapeQueries() {
        DialectConfig dialectConfig = perDialectConfig.get(dialect);
        if (dialectConfig != null && dialectConfig.escapeQueries != null) {
            return dialectConfig.escapeQueries;
        }
        return true;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return SqlQueryConfiguration.DialectConfiguration.class;
    }

    private static class DialectConfig {
        Boolean escapeQueries;
        String positionalFormatter;
    }
}
