/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.*;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import static io.micronaut.data.annotation.GeneratedValue.Type.*;

import java.sql.Blob;
import java.sql.Clob;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link QueryBuilder} that builds SQL queries.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SqlQueryBuilder extends AbstractSqlLikeQueryBuilder implements QueryBuilder {

    /**
     * The start of an IN expression.
     */
    public static final String IN_EXPRESSION_START = " ?$IN(";
    /**
     * Annotation used to represent join tables.
     */
    private static final String ANN_JOIN_TABLE = "io.micronaut.data.jdbc.annotation.JoinTable";
    private static final String BLANK_SPACE = " ";
    private static final String SEQ_SUFFIX = "_seq";
    private static final String INSERT_INTO = "INSERT INTO ";
    private static final String JDBC_REPO_ANNOTATION = "io.micronaut.data.jdbc.annotation.JdbcRepository";

    private Dialect dialect = Dialect.ANSI;

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
                                .enumValue(JDBC_REPO_ANNOTATION, "dialectName", Dialect.class)
                                .orElse(Dialect.ANSI)
                    );

        }
    }

    /**
     * Default constructor.
     */
    public SqlQueryBuilder() {
    }

    /**
     * @param dialect The dialect
     */
    public SqlQueryBuilder(Dialect dialect) {
        ArgumentUtils.requireNonNull("dialect", dialect);
        this.dialect = dialect;
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
    public @NonNull String buildBatchCreateTableStatement (@NonNull PersistentEntity... entities) {
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
    public @NonNull String buildBatchDropTableStatement (@NonNull PersistentEntity... entities) {
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
        String sql = "DROP TABLE " + tableName + ";";
        Collection<Association> foreignKeyAssociations = getJoinTableAssociations(entity.getPersistentProperties());
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
    public @NonNull String buildJoinTableInsert(
            @NonNull PersistentEntity entity,
            @NonNull Association association) {
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

            final PersistentEntity associatedEntity = association.getAssociatedEntity();
            final String[] joinColumns = resolveJoinTableColumns(
                    entity,
                    associatedEntity,
                    association,
                    entity.getIdentity(),
                    associatedEntity.getIdentity(),
                    namingStrategy);

            final String columnStrs =
                    Arrays.stream(joinColumns).map(this::quote).collect(Collectors.joining(","));
            return INSERT_INTO + quote(joinTableName) +
                    " (" + columnStrs + ") VALUES (?, ?)";
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
        StringBuilder builder = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");

        ArrayList<PersistentProperty> props = new ArrayList<>(entity.getPersistentProperties());
        PersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            props.add(0, identity);
        }

        List<String> createStatements = new ArrayList<>();
        String schema = entity.getAnnotationMetadata().stringValue(MappedEntity.class, SqlMembers.SCHEMA).orElse(null);
        if (StringUtils.isNotEmpty(schema)) {
            if (escape) {
                schema = quote(schema);
            }
            createStatements.add("CREATE SCHEMA " + schema + ";");
        }

        Collection<Association> foreignKeyAssociations = getJoinTableAssociations(props);

        if (CollectionUtils.isNotEmpty(foreignKeyAssociations)) {
            for (Association association : foreignKeyAssociations) {
                StringBuilder joinTableBuilder = new StringBuilder("CREATE TABLE ");
                PersistentEntity associatedEntity = association.getAssociatedEntity();
                NamingStrategy namingStrategy = entity.getNamingStrategy();
                String joinTableName = association.getAnnotationMetadata()
                        .stringValue(ANN_JOIN_TABLE, "name")
                        .orElseGet(() ->
                                namingStrategy.mappedName(association)
                        );
                if (escape) {
                    joinTableName = quote(joinTableName);
                }
                joinTableBuilder.append(joinTableName).append(" (");
                PersistentProperty associatedId = associatedEntity.getIdentity();
                String[] joinColumnNames = resolveJoinTableColumns(entity, associatedEntity, association, identity, associatedId, namingStrategy);
                //noinspection ConstantConditions
                joinTableBuilder.append(addTypeToColumn(identity, false, joinColumnNames[0], true))
                        .append(',')
                        .append(addTypeToColumn(associatedId, false, joinColumnNames[1], true));
                joinTableBuilder.append(")");
                if (dialect != Dialect.ORACLE) {
                    joinTableBuilder.append(';');
                }
                createStatements.add(joinTableBuilder.toString());
            }
        }

        List<String> columns = new ArrayList<>(props.size());

        for (PersistentProperty prop : props) {
            boolean isAssociation = false;
            if (prop instanceof Association) {
                isAssociation = true;
                Association association = (Association) prop;
                if (association.isForeignKey()) {
                    continue;
                }
            }

            if (prop instanceof Embedded) {
                Embedded embedded = (Embedded) prop;
                PersistentEntity embeddedEntity = embedded.getAssociatedEntity();
                Collection<? extends PersistentProperty> embeddedProperties = embeddedEntity.getPersistentProperties();
                for (PersistentProperty embeddedProperty : embeddedProperties) {
                    String explicitColumn = embeddedProperty.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
                    String column = explicitColumn != null ? explicitColumn : entity.getNamingStrategy().mappedName(
                            prop.getName() + embeddedProperty.getCapitilizedName()
                    );

                    if (escape) {
                        column = quote(column);
                    }

                    boolean required;
                    if (prop.isOptional()) {
                        required = false;
                    } else {
                        required = embeddedProperty.isRequired() || prop.getAnnotationMetadata().hasStereotype(Id.class);
                    }
                    column = addTypeToColumn(embeddedProperty, embeddedProperty instanceof Association, column, required);
                    column = addGeneratedStatementToColumn(identity, prop, column);
                    columns.add(column);
                }

            } else {
                String column = getColumnName(prop);
                if (escape) {
                    column = quote(column);
                }
                column = addTypeToColumn(prop, isAssociation, column, prop.isRequired());
                column = addGeneratedStatementToColumn(identity, prop, column);
                columns.add(column);
            }

        }
        builder.append(String.join(",", columns));
        if (identity instanceof Embedded) {
            Embedded embedded = (Embedded) identity;
            PersistentEntity embeddedId = embedded.getAssociatedEntity();
            List<String> primaryKeyColumns = new ArrayList<>();
            for (PersistentProperty embeddedProperty : embeddedId.getPersistentProperties()) {
                String explicitColumn = embeddedProperty.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
                String column = explicitColumn != null ? explicitColumn : entity.getNamingStrategy().mappedName(
                        identity.getName() + embeddedProperty.getCapitilizedName()
                );
                if (escape) {
                    column = quote(column);
                }
                primaryKeyColumns.add(column);
            }
            builder.append(", PRIMARY KEY(").append(String.join(",", primaryKeyColumns)).append(')');
        }
        if (dialect == Dialect.ORACLE) {
            builder.append(")");
        } else {
            builder.append(");");
        }

        if (identity != null && identity.isGenerated()) {
             GeneratedValue.Type idGeneratorType = identity.getAnnotationMetadata()
                    .enumValue(GeneratedValue.class, GeneratedValue.Type.class)
                    .orElseGet(this::selectAutoStrategy);
            boolean isSequence = idGeneratorType == GeneratedValue.Type.SEQUENCE || dialect == Dialect.ORACLE;
            if (isSequence) {
                final String createSequenceStatement = identity.getAnnotationMetadata().stringValue(GeneratedValue.class, "definition")
                        .orElseGet(() -> {
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
                            return createSequenceStmt;
                        });
                createStatements.add(createSequenceStatement);
            }
        }
        createStatements.add(builder.toString());
        return createStatements.toArray(new String[0]);
    }

    @Override
    protected String getTableAsKeyword() {
        return BLANK_SPACE;
    }

    private String addGeneratedStatementToColumn(PersistentProperty identity, PersistentProperty prop, String column) {
        if (prop.isGenerated()) {
            GeneratedValue.Type type = prop.getAnnotationMetadata().enumValue(GeneratedValue.class, GeneratedValue.Type.class)
                    .orElse(AUTO);

            if (type == AUTO) {
                if (dialect == Dialect.ORACLE) {
                    type = SEQUENCE;
                } else {
                    type = IDENTITY;
                }
            }
            switch (dialect) {
                case POSTGRES:
                    if (type == SEQUENCE) {
                        if (prop == identity) {
                            column += " PRIMARY KEY NOT NULL";
                        } else {
                            column += " NOT NULL";
                        }
                    } else {
                        if (prop == identity) {
                            column += " GENERATED ALWAYS AS IDENTITY";
                        } else {
                            column += " NOT NULL";
                        }
                    }
                    break;
                case SQL_SERVER:
                    if (type == SEQUENCE) {
                        if (prop == identity) {
                            column += " PRIMARY KEY NOT NULL";
                        }
                    } else {
                        if (prop == identity) {
                            column += " PRIMARY KEY";
                        }
                        column += " IDENTITY(1,1) NOT NULL";
                    }
                    break;
                case ORACLE:
                    // for Oracle we use sequences so just add NOT NULL
                    // then alter the table for sequences
                    if (prop == identity) {
                        column += " PRIMARY KEY NOT NULL";
                    } else {
                        column += " NOT NULL";
                    }
                break;
                default:
                    column += " AUTO_INCREMENT";
                    if (prop == identity) {
                        column += " PRIMARY KEY";
                    }
            }
        }
        return column;
    }

    @NonNull
    private String[] resolveJoinTableColumns(@NonNull PersistentEntity entity, PersistentEntity associatedEntity, Association association, PersistentProperty identity, PersistentProperty associatedId, NamingStrategy namingStrategy) {
        List<AnnotationValue<MappedProperty>> joinColumns = association.getAnnotationMetadata().findAnnotation(ANN_JOIN_TABLE)
                .map(av -> av.getAnnotations("joinColumns", MappedProperty.class)).orElse(Collections.emptyList());
        if (identity == null) {
            throw new MappingException("Cannot have a foreign key association without an ID on entity: " + entity.getName());
        }
        if (associatedId == null) {
            throw new MappingException("Cannot have a foreign key association without an ID on entity: " + associatedEntity.getName());
        }
        String[] joinColumnDefinitions;
        if (CollectionUtils.isEmpty(joinColumns)) {

            String thisName = namingStrategy.mappedName(entity.getDecapitalizedName() + namingStrategy.getForeignKeySuffix());
            String thatName = namingStrategy.mappedName(associatedEntity.getDecapitalizedName() + namingStrategy.getForeignKeySuffix());
            joinColumnDefinitions = new String[] { thisName, thatName };

        } else {
            if (joinColumns.size() != 2) {
                throw new MappingException("Expected exactly 2 join columns for association [" + association.getName() + "] of entity: " + entity.getName());
            } else {
                String thisName = joinColumns.get(0).stringValue().orElseGet(() ->
                        namingStrategy.mappedName(entity.getDecapitalizedName() + namingStrategy.getForeignKeySuffix())
                );
                String thatName = joinColumns.get(1).stringValue().orElseGet(() ->
                        namingStrategy.mappedName(associatedEntity.getDecapitalizedName() + namingStrategy.getForeignKeySuffix())
                );
                joinColumnDefinitions = new String[] { thisName, thatName };
            }
        }
        return joinColumnDefinitions;
    }

    @NonNull
    private Collection<Association> getJoinTableAssociations(Collection<? extends PersistentProperty> props) {
        return props.stream().filter(p -> {
                if (p instanceof Association) {
                    Association a = (Association) p;
                    return isForeignKeyWithJoinTable(a);
                }
                return false;
            }).map(p -> (Association) p).collect(Collectors.toList());
    }

    @Override
    protected void selectAllColumns(QueryState queryState) {
        PersistentEntity entity = queryState.getEntity();
        String alias = queryState.getCurrentAlias();
        StringBuilder queryBuffer = queryState.getQuery();
        String columns = selectAllColumns(entity, alias);
        queryBuffer.append(columns);

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
                    List<PersistentProperty> associatedProperties = getPropertiesThatAreColumns(associatedEntity);
                    if (association.isForeignKey()) {
                        // in the case of a foreign key association the ID is not in the table
                        // so we need to retrieve it
                        PersistentProperty identity = associatedEntity.getIdentity();
                        if (identity != null) {
                            associatedProperties.add(0, identity);
                        }
                    }
                    if (CollectionUtils.isNotEmpty(associatedProperties)) {
                        queryBuffer.append(COMMA);

                        String aliasName = getAliasName(joinPath);
                        String joinPathAlias = getPathOnlyAliasName(joinPath);
                        String columnNames = associatedProperties.stream()
                                .map(p -> {
                                    String columnName = getColumnName(p);
                                    return aliasName + DOT + quote(columnName) + AS_CLAUSE + joinPathAlias + columnName ;
                                })
                                .collect(Collectors.joining(","));
                        queryBuffer.append(columnNames);
                    }

                }
            }
        }
    }

    /**
     * Selects all columns for the given entity and alias.
     * @param entity The entity
     * @param alias The alias
     * @return The column selection string
     */
    public String selectAllColumns(PersistentEntity entity, String alias) {
        String columns;
        boolean escape = shouldEscape(entity);
        List<PersistentProperty> persistentProperties = getPropertiesThatAreColumns(entity);
        if (CollectionUtils.isNotEmpty(persistentProperties)) {
            PersistentProperty identity = entity.getIdentity();
            if (identity != null) {
                persistentProperties.add(0, identity);
            }

            columns = persistentProperties.stream()
                    .map(p -> {
                        if (p instanceof Association) {
                            Association association = (Association) p;
                            if (association.getKind() == Relation.Kind.EMBEDDED) {
                                PersistentEntity embeddedEntity = association.getAssociatedEntity();
                                List<PersistentProperty> embeddedProps = getPropertiesThatAreColumns(embeddedEntity);
                                return embeddedProps.stream().map(ep -> {
                                            String columnName = ep.getAnnotationMetadata().stringValue(MappedProperty.class).orElseGet(() ->
                                                    entity.getNamingStrategy().mappedName(association.getName() + ep.getCapitilizedName())
                                            );
                                            if (escape) {
                                                columnName = quote(columnName);
                                            }
                                            return alias + DOT + columnName;
                                        }
                                ).collect(Collectors.joining(","));
                            }
                        }
                        return p.getAnnotationMetadata().stringValue(DataTransformer.class, "read")
                                    .map(str -> str + AS_CLAUSE + p.getPersistedName())
                                    .orElseGet(() -> {
                                        String columnName = getColumnName(p);
                                        if (escape) {
                                            columnName = quote(columnName);
                                        }
                                        return alias + DOT + columnName;
                                    });
                    })
                    .collect(Collectors.joining(","));
        } else {
            columns = "*";
        }
        return columns;
    }

    @NonNull
    private List<PersistentProperty> getPropertiesThatAreColumns(PersistentEntity entity) {
        return entity.getPersistentProperties()
                .stream()
                .filter(pp -> {
                    if (pp instanceof Association) {
                        Association association = (Association) pp;
                        return !association.isForeignKey();
                    }
                    return true;
                })
                .collect(Collectors.toList());
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
        StringBuilder builder = new StringBuilder(INSERT_INTO);
        final String unescapedTableName = getUnescapedTableName(entity);
        String tableName = getTableName(entity);
        boolean escape = shouldEscape(entity);
        builder.append(tableName);
        builder.append(" (");

        Collection<? extends PersistentProperty> persistentProperties = entity.getPersistentProperties();
        Map<String, String> parameters = new LinkedHashMap<>(persistentProperties.size());
        Map<String, DataType> parameterTypes = new LinkedHashMap<>(persistentProperties.size());
        boolean hasProperties = CollectionUtils.isNotEmpty(persistentProperties);
        List<String> values = new ArrayList<>(persistentProperties.size());
        if (hasProperties) {
            List<String> columnNames = new ArrayList<>(persistentProperties.size());
            for (PersistentProperty prop : persistentProperties) {
                if (!prop.isGenerated()) {
                    if (prop instanceof Association) {
                        Association association = (Association) prop;
                        if (association instanceof Embedded) {
                            Embedded embedded = (Embedded) association;
                            PersistentEntity embeddedEntity = association.getAssociatedEntity();
                            Collection<? extends PersistentProperty> embeddedProps = embeddedEntity.getPersistentProperties();
                            for (PersistentProperty embeddedProp : embeddedProps) {
                                String explicitColumn = embeddedProp.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
                                addWriteExpression(values, prop);
                                parameters.put(String.valueOf(values.size()), prop.getName() + "." + embeddedProp.getName());
                                if (explicitColumn != null) {
                                    if (escape) {
                                        explicitColumn = quote(explicitColumn);
                                    }
                                    columnNames.add(explicitColumn);
                                } else {
                                    NamingStrategy namingStrategy = entity.getNamingStrategy();
                                    String columnName = namingStrategy.mappedName(
                                            embedded,
                                            embeddedProp
                                    );
                                    if (escape) {
                                        columnName = quote(columnName);
                                    }
                                    columnNames.add(columnName);
                                }
                            }
                        } else if (!association.isForeignKey()) {
                            parameterTypes.put(prop.getName(), prop.getDataType());
                            addWriteExpression(values, prop);
                            parameters.put(String.valueOf(values.size()), prop.getName());
                            String columnName = getColumnName(prop);
                            if (escape) {
                                columnName = quote(columnName);
                            }
                            columnNames.add(columnName);
                        }
                    } else {
                        parameterTypes.put(prop.getName(), prop.getDataType());
                        addWriteExpression(values, prop);
                        parameters.put(String.valueOf(values.size()), prop.getName());
                        String columnName = getColumnName(prop);
                        if (escape) {
                            columnName = quote(columnName);
                        }
                        columnNames.add(columnName);
                    }
                }
            }
            builder.append(String.join(",", columnNames));
        }

        PersistentProperty identity = entity.getIdentity();
        if (identity != null) {

            boolean assignedOrSequence = false;
            Optional<AnnotationValue<GeneratedValue>> generated = identity.findAnnotation(GeneratedValue.class);
            boolean isSequence = false;
            if (generated.isPresent()) {
                GeneratedValue.Type idGeneratorType = generated
                        .flatMap(av -> av.enumValue(GeneratedValue.Type.class))
                        .orElseGet(this::selectAutoStrategy);
                if (idGeneratorType == GeneratedValue.Type.SEQUENCE || dialect == Dialect.ORACLE) {
                    isSequence = true;
                    assignedOrSequence = true;
                }
            } else {
                assignedOrSequence = true;
            }
            if (assignedOrSequence) {
                if (hasProperties) {
                    builder.append(COMMA);
                }
                if (identity instanceof Embedded) {
                    List<String> columnNames = new ArrayList<>(persistentProperties.size());
                    PersistentEntity embeddedEntity = ((Embedded) identity).getAssociatedEntity();
                    Collection<? extends PersistentProperty> embeddedProps = embeddedEntity.getPersistentProperties();
                    for (PersistentProperty embeddedProp : embeddedProps) {
                        String explicitColumn = embeddedProp.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
                        addWriteExpression(values, embeddedProp);
                        parameters.put(String.valueOf(values.size()), identity.getName() + "." + embeddedProp.getName());
                        if (explicitColumn != null) {
                            if (escape) {
                                explicitColumn = quote(explicitColumn);
                            }
                            columnNames.add(explicitColumn);
                        } else {
                            NamingStrategy namingStrategy = entity.getNamingStrategy();
                            String columnName = namingStrategy.mappedName(identity.getName() + embeddedProp.getCapitilizedName());
                            if (escape) {
                                columnName = quote(columnName);
                            }
                            columnNames.add(columnName);
                        }
                    }
                    builder.append(String.join(",", columnNames));

                } else {
                    String columnName = getColumnName(identity);
                    if (escape) {
                        columnName = quote(columnName);
                    }
                    builder.append(columnName);
                    if (isSequence) {
                        final String sequenceName = resolveSequenceName(identity, unescapedTableName);
                        if (dialect == Dialect.ORACLE) {
                            values.add(quote(sequenceName) + ".nextval");
                        } else if (dialect == Dialect.POSTGRES) {
                            values.add("nextval('" + sequenceName + "')");
                        } else if (dialect == Dialect.SQL_SERVER) {
                            values.add("NEXT VALUE FOR " + quote(sequenceName));
                        }
                    } else {
                        addWriteExpression(values, identity);
                        parameters.put(String.valueOf(values.size()), identity.getName());
                    }
                }
            }
        }

        builder.append(CLOSE_BRACKET);
        builder.append(" VALUES (");
        builder.append(String.join(String.valueOf(COMMA), values));
        builder.append(CLOSE_BRACKET);
        return QueryResult.of(
                builder.toString(),
                parameters,
                parameterTypes,
                Collections.emptySet()
        );
    }

    private String resolveSequenceName(PersistentProperty identity, String unescapedTableName) {
        return identity.getAnnotationMetadata().stringValue(GeneratedValue.class, "ref")
                                    .orElseGet(() -> unescapedTableName + SEQ_SUFFIX);
    }

    private boolean addWriteExpression(List<String> values, PersistentProperty property) {
        if (property.getDataType() == DataType.JSON && dialect == Dialect.POSTGRES) {
            return values.add("to_json(?::json)");
        } else {
            return values.add(property.getAnnotationMetadata().stringValue(DataTransformer.class, "write").orElse("?"));
        }
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
    protected void encodeInExpression(StringBuilder whereClause, Placeholder placeholder) {
        whereClause
                .append(IN_EXPRESSION_START)
                .append(placeholder.getKey())
                .append(CLOSE_BRACKET);
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

    @Override
    protected void appendUpdateSetParameter(StringBuilder queryString, PersistentProperty prop, Placeholder param) {
        // to_json(?::json)
        if (prop.getDataType() == DataType.JSON) {
            switch (dialect) {
                case MYSQL:
                    queryString.append("CONVERT(? USING UTF8MB4)");
                    break;
                case POSTGRES:
                    queryString.append("to_json(?::json)");
                    break;
                default:
                    super.appendUpdateSetParameter(queryString, prop, param);
            }
        } else {
            super.appendUpdateSetParameter(queryString, prop, param);
        }
    }

    @Override
    protected String[] buildJoin(
            String alias,
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
        joinAliases = new String[associationPath.length];
        StringBuilder pathSoFar = new StringBuilder();
        for (int i = 0; i < associationPath.length; i++) {
            Association association = associationPath[i];
            String associationName = association.getName();
            pathSoFar.append(associationName);
            String existingAlias = appliedJoinPaths.get(alias + DOT + associationName);
            if (existingAlias != null) {
                joinAliases[i] = existingAlias;
                alias = existingAlias;
            } else {
                PersistentEntity associatedEntity = association.getAssociatedEntity();
                int finalI = i;
                JoinPath joinPathToUse = queryState.getQueryModel().getJoinPath(pathSoFar.toString())
                          .orElseGet(() ->
                                  new JoinPath(
                                          pathSoFar.toString(),
                                          Arrays.copyOfRange(associationPath, 0, finalI + 1),
                                          joinPath.getJoinType(),
                                          joinPath.getAlias().orElse(null))
                          );
                joinAliases[i] = getAliasName(joinPathToUse);
                PersistentProperty identity = associatedEntity.getIdentity();
                if (identity == null) {
                    throw new IllegalArgumentException("Associated entity [" + associatedEntity.getName() + "] defines no ID. Cannot join.");
                }

                if (association.isForeignKey()) {
                    String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
                    final PersistentEntity associationOwner = association.getOwner();
                    if (StringUtils.isNotEmpty(mappedBy)) {
                        PersistentProperty mappedProp = associatedEntity.getPropertyByName(mappedBy);
                        if (mappedProp == null) {
                            throw new MappingException("Foreign key association with mappedBy references a property that doesn't exist [" + mappedBy + "] of entity: " + associatedEntity.getName());
                        }

                        final PersistentProperty associatedId = associationOwner.getIdentity();
                        if (associatedId == null) {
                            throw new MappingException("Cannot join on entity [" + associationOwner.getName() + "] that has no declared ID");
                        }
                        final boolean escape = shouldEscape(associationOwner);
                        target.append(joinType)
                                .append(getTableName(associatedEntity))
                                .append(SPACE)
                                .append(joinAliases[i])
                                .append(" ON ")
                                .append(alias)
                                .append(DOT)
                                .append(escape ? quote(getColumnName(associatedId)) : getColumnName(associatedId))
                                .append('=')
                                .append(joinAliases[i])
                                .append(DOT)
                                .append(escape ? quote(getColumnName(mappedProp)) : getColumnName(mappedProp));
                    } else {
                        final PersistentProperty associatedId = associationOwner.getIdentity();
                        if (associatedId == null) {
                            throw new MappingException("Cannot join on entity [" + associationOwner.getName() + "] that has no declared ID");
                        }
                        target.append(joinType);
                        final boolean escape = shouldEscape(associationOwner);
                        NamingStrategy namingStrategy = associationOwner.getNamingStrategy();
                        String joinTableName = association.getAnnotationMetadata()
                                .stringValue(ANN_JOIN_TABLE, "name")
                                .orElseGet(() ->
                                        namingStrategy.mappedName(association)
                                );
                        String[] joinColumnNames = resolveJoinTableColumns(associationOwner, associatedEntity, association, identity, associatedEntity.getIdentity(), namingStrategy);
                        String joinTableAlias = joinAliases[i] + joinTableName + "_";
                        String associatedTableName = getTableName(associatedEntity);
                        target.append(joinTableName)
                              .append(SPACE)
                              .append(joinTableAlias)
                              .append(" ON ")
                              .append(alias)
                              .append(DOT)
                              .append(escape ? quote(getColumnName(associatedId)) : getColumnName(associatedId))
                              .append('=')
                              .append(joinTableAlias)
                              .append(DOT)
                              .append(joinColumnNames[0])
                              .append(SPACE)
                              .append(joinType)
                              .append(associatedTableName)
                              .append(SPACE)
                              .append(joinAliases[i])
                              .append(" ON ")
                              .append(joinTableAlias)
                              .append(DOT)
                              .append(joinColumnNames[1])
                              .append('=')
                              .append(joinAliases[i])
                              .append(DOT)
                              .append(escape ? quote(getColumnName(associatedEntity.getIdentity())) : getColumnName(associatedEntity.getIdentity()));
                    }
                } else {
                    final PersistentEntity associationOwner = association.getOwner();
                    final boolean escape = shouldEscape(associationOwner);
                    target.append(joinType)
                            .append(getTableName(associatedEntity))
                            .append(SPACE)
                            .append(joinAliases[i])
                            .append(" ON ")
                            .append(alias)
                            .append(DOT)
                            .append(escape ? quote(getColumnName(association)) : getColumnName(association))
                            .append('=')
                            .append(joinAliases[i])
                            .append(DOT)
                            .append(escape ? quote(getColumnName(identity)) : getColumnName(identity));
                }
                alias = joinAliases[i];
            }
            pathSoFar.append(DOT);
        }
        return joinAliases;
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
            case POSTGRES:
                // Postgres requires quoted identifiers to be in lower case
                return '"' + persistedName.toLowerCase(Locale.ENGLISH) + '"';
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
    protected final boolean computePropertyPaths() {
        return true;
    }

    @Override
    protected boolean isAliasForBatch() {
        return false;
    }

    @Override
    protected Placeholder formatParameter(int index) {
        return new Placeholder("?", String.valueOf(index));
    }

    /**
     * Selects the default fallback strategy. For a generated value.
     * @return The generated value
     */
    protected GeneratedValue.Type selectAutoStrategy() {
        return GeneratedValue.Type.AUTO;
    }

    private String addTypeToColumn(PersistentProperty prop, boolean isAssociation, String column, boolean required) {
        AnnotationMetadata annotationMetadata = prop.getAnnotationMetadata();
        String definition = annotationMetadata.stringValue(MappedProperty.class, "definition").orElse(null);
        DataType dataType = prop.getDataType();
        if (definition != null) {
            return column + " " + definition;
        }

        switch (dataType) {
            case STRING:
                column += " VARCHAR(255)";
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
                    column += " DATETIME";
                    if (required) {
                        column += " NOT NULL";
                    }
                } else if (dialect == Dialect.MYSQL) {
                    // mysql doesn't allow timestamp without default
                    column += " TIMESTAMP DEFAULT NOW()";
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
            case INTEGER:
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
            case BIGDECIMAL:
                if (dialect == Dialect.ORACLE) {
                    column += " FLOAT(126)";
                } else {
                    column += " DECIMAL";
                }
                if (required) {
                    column += " NOT NULL";
                }
                break;
            case FLOAT:
                if (dialect == Dialect.ORACLE || dialect == Dialect.SQL_SERVER) {
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
                if (dialect == Dialect.ORACLE) {
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
            default:
                if (isAssociation) {
                    Association association = (Association) prop;
                    PersistentEntity associatedEntity = association.getAssociatedEntity();

                    PersistentProperty identity = associatedEntity.getIdentity();
                    if (identity != null) {
                        return addTypeToColumn(identity, false, column, required);
                    }
                } else {
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
        }
        return column;
    }
}
