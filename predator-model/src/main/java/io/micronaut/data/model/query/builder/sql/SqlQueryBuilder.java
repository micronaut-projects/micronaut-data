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
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;

import java.util.*;
import java.util.stream.Collectors;

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

    private Dialect dialect = Dialect.ANSI;

    /**
     * Constructor with annotation metadata.
     * @param annotationMetadata The annotation metadata
     */
    @Creator
    public SqlQueryBuilder(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata != null) {
            this.dialect = annotationMetadata.findAnnotation(Repository.class)
                                            .flatMap(av -> av.enumValue("dialect", Dialect.class))
                                            .orElse(Dialect.ANSI);
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

    /**
     * Builds the create table statement. Designed for testing and not production usage. For production a
     *  SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entities the entities
     * @return The table
     */
    @Experimental
    public @NonNull String buildCreateTables(@NonNull PersistentEntity... entities) {
        return Arrays.stream(entities).map(this::buildCreateTable)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Builds the create table statement. Designed for testing and not production usage. For production a
     * SQL migration tool such as Flyway or Liquibase is recommended.
     *
     * @param entity The entity
     * @return The table
     */
    @Experimental
    public @NonNull String buildCreateTable(@NonNull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);
        String tableName = getTableName(entity);
        StringBuilder builder = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");

        ArrayList<PersistentProperty> props = new ArrayList<>(entity.getPersistentProperties());
        PersistentProperty identity = entity.getIdentity();
        if (identity != null) {
            props.add(0, identity);
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
            String column = getColumnName(prop);

            column = addTypeToColumn(prop, isAssociation, column);
            if (prop.isGenerated()) {
                switch (dialect) {
                    case POSTGRES:
                        column += " GENERATED ALWAYS AS IDENTITY";
                    break;
                    case SQL_SERVER:
                        if (prop == identity) {
                            column += " PRIMARY KEY";
                        }
                        column += " IDENTITY(1,1) NOT NULL";
                    break;
                    default:
                        // TODO: handle more dialects
                        column += " AUTO_INCREMENT";
                        if (prop == identity) {
                            column += " PRIMARY KEY";
                        }
                }
            }
            columns.add(column);
        }
        builder.append(String.join(",", columns));
        builder.append(");");
        return builder.toString();
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
                    if (association.isForeignKey()) {
                        throw new IllegalArgumentException("Join fetching is not currently supported with foreign key association. Specify a manual query");
                    }
                    if (association instanceof Embedded) {
                        // joins on embedded don't make sense
                        continue;
                    }
                    PersistentEntity associatedEntity = association.getAssociatedEntity();
                    if (associatedEntity == null) {
                        throw new IllegalArgumentException("Join path specified with associated entity that doesn't exist");
                    }
                    List<PersistentProperty> associatedProperties = getPropertiesThatAreColumns(associatedEntity);
                    PersistentProperty identity = associatedEntity.getIdentity();
                    if (identity != null) {
                        associatedProperties.add(0, identity);
                    }
                    if (CollectionUtils.isNotEmpty(associatedProperties)) {
                        queryBuffer.append(COMMA);

                        String aliasName = getAliasName(association);
                        String columnNames = associatedProperties.stream()
                                .map(p -> {
                                    String columnName = getColumnName(p);
                                    return aliasName + DOT + columnName + AS_CLAUSE + '_' + getColumnName(association) + '_' + columnName;
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
    protected String selectAllColumns(PersistentEntity entity, String alias) {
        String columns;
        List<PersistentProperty> persistentProperties = getPropertiesThatAreColumns(entity);
        if (CollectionUtils.isNotEmpty(persistentProperties)) {
            PersistentProperty identity = entity.getIdentity();
            if (identity != null) {
                persistentProperties.add(0, identity);
            }

            String columnNames = persistentProperties.stream()
                    .map(p -> alias + DOT + getColumnName(p))
                    .collect(Collectors.joining(","));

            columns = columnNames;
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
            default:
                joinType = " INNER JOIN ";
        }
        return joinType;
    }

    @Nullable
    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, PersistentEntity entity) {
        StringBuilder builder = new StringBuilder("INSERT INTO ");
        builder.append(getTableName(entity));
        builder.append(" (");

        List<PersistentProperty> persistentProperties = entity.getPersistentProperties();
        Map<String, String> parameters = new LinkedHashMap<>(persistentProperties.size());
        boolean hasProperties = CollectionUtils.isNotEmpty(persistentProperties);
        int index = 1;
        if (hasProperties) {
            List<String> columnNames = new ArrayList<>(persistentProperties.size());
            for (PersistentProperty prop : persistentProperties) {
                if (!prop.isGenerated()) {
                    if (prop instanceof Association) {
                        Association association = (Association) prop;
                        Relation.Kind kind = association.getKind();
                        switch (kind) {
                            case MANY_TO_ONE:
                            case ONE_TO_ONE:
                                parameters.put(prop.getName(), String.valueOf(index++));
                                columnNames.add(getColumnName(prop));
                                continue;
                            case EMBEDDED:
                                // TODO: handle embedded
                            default:
                                // skip, for foreign key

                        }
                    } else {
                        parameters.put(prop.getName(), String.valueOf(index++));
                        columnNames.add(getColumnName(prop));
                    }
                }
            }
            builder.append(String.join(",", columnNames));
        }

        PersistentProperty identity = entity.getIdentity();
        if (identity != null) {

            boolean assignedOrSequence = false;
            Optional<AnnotationValue<GeneratedValue>> generated = identity.findAnnotation(GeneratedValue.class);
            if (generated.isPresent()) {
                GeneratedValue.Type idGeneratorType = generated
                        .flatMap(av -> av.enumValue(GeneratedValue.Type.class))
                        .orElseGet(this::selectAutoStrategy);
                if (idGeneratorType == GeneratedValue.Type.SEQUENCE) {
                    assignedOrSequence = true;
                }
            } else {
                assignedOrSequence = true;
            }
            if (assignedOrSequence) {
                if (hasProperties) {
                    builder.append(COMMA);
                }
                builder.append(getColumnName(identity));
                parameters.put(identity.getName(), String.valueOf(index++));
            }
        }

        builder.append(CLOSE_BRACKET);
        builder.append(" VALUES (");
        for (int i = 1; i < index; i++) {
            builder.append('?');
            if (i < index - 1) {
                builder.append(COMMA);
            }
        }
        builder.append(CLOSE_BRACKET);
        return QueryResult.of(
                builder.toString(),
                parameters
        );
    }

    @NonNull
    @Override
    public QueryResult buildPagination(@NonNull Pageable pageable) {
        StringBuilder builder = new StringBuilder(" ");
        int size = pageable.getSize();
        long from = pageable.getOffset();
        long to = from + size;
        if (to < 0) {
            // handle overflow
            from = 0;
            to = size;
        }
        switch (dialect) {
            case H2:
            case MYSQL:
                if (from == 0) {
                    builder.append("LIMIT ").append(to);
                } else {
                    builder.append("LIMIT ").append(from).append(',').append(to);
                }
            break;
            case POSTGRES:
                builder.append("LIMIT ").append(to).append(" ");
                if (from != 0) {
                    builder.append("OFFSET ").append(to);
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
                    builder.append("OFFSET ").append(to).append(" ROWS ");
                }
                builder.append("FETCH NEXT ").append(to).append(" ROWS ONLY ");
            break;
        }
        return QueryResult.of(
                builder.toString(),
                Collections.emptyMap()
        );
    }

    @Override
    protected void encodeInExpression(StringBuilder whereClause, Placeholder placeholder) {
        whereClause
                .append(IN_EXPRESSION_START)
                .append(placeholder.getKey())
                .append(CLOSE_BRACKET);
    }

    @Override
    public String getTableName(PersistentEntity entity) {
        return entity.getPersistedName();
    }

    @Override
    protected String buildJoin(String alias, Association association, String joinType, StringBuilder target) {
        PersistentEntity associatedEntity = association.getAssociatedEntity();
        if (associatedEntity == null) {
            throw new IllegalArgumentException("Associated entity not found for association: " + association.getName());
        }
        String joinAlias = getAliasName(association);
        PersistentProperty identity = associatedEntity.getIdentity();
        if (identity == null) {
            throw new IllegalArgumentException("Associated entity [" + associatedEntity.getName() + "] defines no ID. Cannot join.");
        }
        target.append(joinType)
              .append(getTableName(associatedEntity))
              .append(SPACE)
              .append(joinAlias)
              .append(" ON ")
              .append(alias)
              .append(DOT)
              .append(getColumnName(association))
              .append('=')
              .append(joinAlias)
              .append(DOT)
              .append(getColumnName(identity));

        return joinAlias;
    }

    /**
     * Quote a column name for the dialect.
     * @param persistedName The persisted name.
     * @return The quoted name
     */
    protected String quote(String persistedName) {
        switch (dialect) {
            case MYSQL:
            case H2:
                return '`' + persistedName + '`';
            case SQL_SERVER:
                return '[' + persistedName + ']';
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
    protected final boolean isApplyManualJoins() {
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

    private String addTypeToColumn(PersistentProperty prop, boolean isAssociation, String column) {
        switch (prop.getDataType()) {
            case STRING:
                column += " VARCHAR(255)";
                break;
            case BYTE:
            case BOOLEAN:
                column += " BIT";
                break;
            case TIMESTAMP:
                if (dialect == Dialect.SQL_SERVER) {
                    // sql server timestamp is an internal type, use datetime instead
                    column += " DATETIME";
                } else if (dialect == Dialect.MYSQL) {
                    // mysql doesn't allow timestamp without default
                    column += " TIMESTAMP DEFAULT NOW()";
                } else {
                    column += " TIMESTAMP";
                }
                break;
            case DATE:
                column += " DATE";
                break;
            case LONG:
                column += " BIGINT";
                break;
            case CHARACTER:
            case INTEGER:
                if (dialect == Dialect.POSTGRES) {
                    column += " INTEGER";
                } else {
                    column += " INT";
                }

                break;
            case BIGDECIMAL:
                column += " DECIMAL";
                break;
            case FLOAT:
                if (dialect == Dialect.POSTGRES || dialect == Dialect.SQL_SERVER) {
                    column += " REAL";
                } else {
                    column += " FLOAT";
                }
                break;
            case BYTE_ARRAY:
                if (dialect == Dialect.POSTGRES) {
                    column += " BYTEA";
                } else {
                    column += " BINARY";
                }
                break;
            case DOUBLE:
                if (dialect == Dialect.POSTGRES || dialect == Dialect.SQL_SERVER) {
                    column += " REAL";
                } else {
                    column += " DOUBLE";
                }
                break;
            case SHORT:
                if (dialect == Dialect.POSTGRES) {
                    column += " SMALLINT";
                } else {
                    column += " TINYINT";
                }

                break;
            default:
                if (isAssociation) {
                    Association association = (Association) prop;
                    PersistentEntity associatedEntity = association.getAssociatedEntity();
                    if (associatedEntity != null) {

                        PersistentProperty identity = associatedEntity.getIdentity();
                        if (identity != null) {
                            return addTypeToColumn(identity, false, column);
                        }
                    }
                }
                column += " OBJECT";
        }
        return column;
    }
}
