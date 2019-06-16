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
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
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
     * Builds the create table statement. Designed for testing and not production usage.
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
     * Builds the create table statement. Designed for testing and not production usage.
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
                // TODO: handle dialects
                column += " AUTO_INCREMENT";
                if (prop == identity) {
                    column += " PRIMARY KEY";
                }
            }
            columns.add(column);
        }
        builder.append(String.join(",", columns));
        builder.append(");");
        return builder.toString();
    }

    @Override
    public String selectAllColumns(PersistentEntity entity, String alias) {
        List<PersistentProperty> persistentProperties = entity.getPersistentProperties()
                .stream()
                .filter(pp -> {
                    if (pp instanceof Association) {
                        Association association = (Association) pp;
                        return !association.isForeignKey();
                    }
                    return true;
                })
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(persistentProperties)) {
            PersistentProperty identity = entity.getIdentity();
            if (identity != null) {
                persistentProperties.add(0, identity);
            }

            return persistentProperties.stream()
                    .map(p -> alias + DOT + getColumnName(p))
                    .collect(Collectors.joining(","));

        } else {
            return "*";
        }
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
            case ANSI:
            case SQL_SERVER:
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
    protected String getTableName(PersistentEntity entity) {
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
    protected String getColumnName(PersistentProperty persistentProperty) {
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
            case DATE:
                column += " TIMESTAMP";
                break;
            case LONG:
                column += " BIGINT";
                break;
            case CHARACTER:
            case INTEGER:
                column += " INT";
                break;
            case BIGDECIMAL:
                column += " DECIMAL";
                break;
            case FLOAT:
                column += " FLOAT";
                break;
            case BYTE_ARRAY:
                column += " BINARY";
                break;
            case DOUBLE:
                column += " DOUBLE";
                break;
            case SHORT:
                column += " TINYINT";
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
