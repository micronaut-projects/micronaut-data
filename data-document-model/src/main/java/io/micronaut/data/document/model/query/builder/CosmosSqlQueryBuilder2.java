/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.document.model.query.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.repeatable.WhereSpecifications;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentAssociationPath;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder2;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;

/**
 * The Azure Cosmos DB sql query builder.
 *
 * @author radovanradic
 * @since 3.9.0
 */
@Internal
public final class CosmosSqlQueryBuilder2 extends SqlQueryBuilder2 {

    private static final NamingStrategy RAW_NAMING_STRATEGY = new NamingStrategies.Raw();
    private static final String JOIN = " JOIN ";
    private static final String IN = " IN ";

    @Creator
    public CosmosSqlQueryBuilder2(AnnotationMetadata annotationMetadata) {
        super(annotationMetadata);
    }

    @Override
    protected String asLiteral(Object value) {
        if (value instanceof Boolean) {
            return value.toString();
        }
        return super.asLiteral(value);
    }

    @Override
    protected boolean traverseEmbedded() {
        return false;
    }

    @Override
    protected SqlSelectionVisitor createSelectionVisitor(AnnotationMetadata annotationMetadata, QueryState queryState, boolean distinct) {
        return new SqlSelectionVisitor(queryState, annotationMetadata, distinct) {

            private static final String VALUE = "VALUE ";
            private static final String SELECT_COUNT = "COUNT(1)";

            @Override
            protected void appendRowCount(String logicalName) {
                query.append(SELECT_COUNT);
            }

            @Override
            protected void appendRowCountDistinct(String logicalName) {
                throw new UnsupportedOperationException("Count distinct is not supported by Micronaut Data Azure Cosmos.");
            }

            @Override
            protected void selectAllColumnsFromJoinPaths(Collection<JoinPath> allPaths, Map<JoinPath, String> joinAliasOverride) {
                // Does nothing since we don't select columns in joins
            }

            @Override
            protected void appendPropertyProjection(QueryPropertyPath propertyPath) {
                query.append(VALUE);
                super.appendPropertyProjection(propertyPath);
            }

            @Override
            protected void appendAssociationProjection(PersistentAssociationPath associationPath) {
                String joinedPath = associationPath.getPath();
                if (!queryState.isJoined(joinedPath)) {
                    query.setLength(query.length() - 1);
                    return;
                }
                String joinAlias = queryState.getJoinAlias(associationPath.getPath());
                selectAllColumns(AnnotationMetadata.EMPTY_METADATA, associationPath.getAssociation().getAssociatedEntity(), joinAlias);
            }

            @Override
            protected void selectAllColumnsAndJoined() {
                query.append(DISTINCT).append(VALUE).append(queryState.getRootAlias());
            }

            @Override
            public void visit(UnaryExpression<?> unaryExpression) {
                query.append(VALUE);
                super.visit(unaryExpression);
            }
        };
    }

    @Override
    protected SqlPredicateVisitor createPredicateVisitor(AnnotationMetadata annotationMetadata, QueryState queryState) {
        return new SqlPredicateVisitor(queryState, annotationMetadata) {

            private static final String IS_NULL = "IS_NULL";
            private static final String IS_DEFINED = "IS_DEFINED";
            private static final String ARRAY_CONTAINS = "ARRAY_CONTAINS";

            @Override
            public void visitIsNull(Expression<?> expression) {
                PersistentPropertyPath propertyPath = requireProperty(expression).getPropertyPath();
                query.append(NOT).append(SPACE).append(IS_DEFINED).append(OPEN_BRACKET);
                appendPropertyRef(propertyPath);
                query.append(CLOSE_BRACKET).append(SPACE).append(OR).append(SPACE);
                query.append(IS_NULL).append(OPEN_BRACKET);
                appendPropertyRef(propertyPath);
                query.append(CLOSE_BRACKET);
            }

            @Override
            public void visitIsNotNull(Expression<?> expression) {
                PersistentPropertyPath propertyPath = requireProperty(expression).getPropertyPath();
                query.append(IS_DEFINED).append(OPEN_BRACKET);
                appendPropertyRef(propertyPath);
                query.append(CLOSE_BRACKET).append(SPACE).append(AND).append(SPACE);
                query.append(NOT).append(SPACE).append(IS_NULL).append(OPEN_BRACKET);
                appendPropertyRef(propertyPath);
                query.append(CLOSE_BRACKET);
            }

            @Override
            public void visitIsEmpty(Expression<?> expression) {
                PersistentPropertyPath propertyPath = requireProperty(expression).getPropertyPath();
                query.append(NOT).append(SPACE).append(IS_DEFINED).append(OPEN_BRACKET);
                appendPropertyRef(propertyPath);
                query.append(CLOSE_BRACKET).append(SPACE).append(OR).append(SPACE);
                query.append(IS_NULL).append(OPEN_BRACKET);
                appendPropertyRef(propertyPath);
                query.append(CLOSE_BRACKET).append(SPACE).append(OR).append(SPACE);
                appendPropertyRef(propertyPath);
                query.append(" = ").append("''");
            }

            @Override
            public void visitIsNotEmpty(Expression<?> expression) {
                PersistentPropertyPath propertyPath = requireProperty(expression).getPropertyPath();
                query.append(IS_DEFINED).append(OPEN_BRACKET);
                appendPropertyRef(propertyPath);
                query.append(CLOSE_BRACKET).append(SPACE).append(AND).append(SPACE);
                query.append(NOT).append(SPACE).append(IS_NULL).append(OPEN_BRACKET);
                appendPropertyRef(propertyPath);
                query.append(CLOSE_BRACKET).append(SPACE).append(AND).append(SPACE);
                appendPropertyRef(propertyPath);
                query.append(" != ").append("''");
            }

            @Override
            public void visitArrayContains(Expression<?> leftExpression, Expression<?> rightExpression) {
                PersistentPropertyPath leftProperty = requireProperty(leftExpression).getPropertyPath();
                query.append(ARRAY_CONTAINS).append(OPEN_BRACKET);
                appendPropertyRef(leftProperty);
                query.append(COMMA);
                appendExpression(rightExpression, leftExpression);
                query.append(COMMA);
                query.append("true").append(CLOSE_BRACKET);
            }

        };
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentEntity entity) {
        return entity.findNamingStrategy().orElse(RAW_NAMING_STRATEGY);
    }

    @Override
    protected NamingStrategy getNamingStrategy(PersistentPropertyPath propertyPath) {
        return propertyPath.findNamingStrategy().orElse(RAW_NAMING_STRATEGY);
    }

    /**
     * We use this method instead of selectAllColumnsFromJoinPaths(Collection, Map)
     * and said method is empty because Cosmos Db has different join logic.
     *
     * @param queryState
     * @param queryBuffer
     * @param allPaths
     * @param joinAliasOverride
     */
    private void appendJoins(QueryState queryState,
                             StringBuilder queryBuffer,
                             Collection<JoinPath> allPaths,
                             @Nullable Map<JoinPath, String> joinAliasOverride) {
        if (CollectionUtils.isEmpty(allPaths)) {
            return;
        }
        String logicalName = queryState.getRootAlias();
        Map<String, String> joinedPaths = new HashMap<>();
        for (JoinPath joinPath : allPaths) {
            Association association = joinPath.getAssociation();
            if (association.isEmbedded()) {
                // joins on embedded don't make sense
                continue;
            }
            String joinAlias = joinAliasOverride == null ? getAliasName(joinPath) : joinAliasOverride.get(joinPath);
            // cannot join family_.children c join family_children.pets p but instead must do
            // join family_.children c join c.pets p (must go via children table)
            String path = logicalName + DOT + joinPath.getPath();
            for (Map.Entry<String, String> entry : joinedPaths.entrySet()) {
                String joinedPath = entry.getKey();
                String prefix = joinedPath + DOT;
                if (path.startsWith(prefix) && !joinedPath.equals(path)) {
                    path = entry.getValue() + DOT + path.replace(prefix, "");
                    break;
                }
            }
            queryBuffer.append(JOIN).append(joinAlias).append(IN).append(path);
            joinedPaths.put(path, joinAlias);
        }
    }

    @Override
    public QueryResult buildSelect(@NonNull AnnotationMetadata annotationMetadata, @NonNull SelectQueryDefinition definition) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        QueryState queryState = new QueryState(definition, true, true);

        List<JoinPath> joinPaths = new ArrayList<>(definition.getJoinPaths());
        joinPaths.sort((o1, o2) -> Comparator.comparingInt(String::length).thenComparing(String::compareTo).compare(o1.getPath(), o2.getPath()));
        for (JoinPath joinPath : joinPaths) {
            queryState.applyJoin(joinPath);
        }

        StringBuilder select = queryState.getQuery();

        select.append(SELECT_CLAUSE);

        buildSelect(
            annotationMetadata,
            queryState,
            definition.selection(),
            false
        );

        select.append(FROM_CLAUSE)
            .append(getTableName(queryState.getEntity()))
            .append(SPACE)
            .append(queryState.getRootAlias());

        appendJoins(queryState, select, definition.getJoinPaths(), null);

        Predicate predicate = definition.predicate();

        if (predicate != null || annotationMetadata.hasStereotype(WhereSpecifications.class) || queryState.getEntity().getAnnotationMetadata().hasStereotype(WhereSpecifications.class)) {
            buildWhereClause(annotationMetadata, predicate, queryState);
        }

        appendOrder(annotationMetadata, definition.order(), queryState);
        appendForUpdate(QueryPosition.END_OF_QUERY, definition, queryState.getQuery());

        return QueryResult.of(
            queryState.getFinalQuery(),
            queryState.getQueryParts(),
            queryState.getParameterBindings(),
            definition.limit(),
            definition.offset(),
            queryState.getJoinPaths()
        );
    }

    @Override
    protected void buildJoin(String joinType, StringBuilder query, QueryState queryState, PersistentAssociationPath joinAssociation, PersistentEntity associationOwner, String currentJoinAlias, String lastJoinAlias) {
        // Does nothing since joins in Cosmos Db work different way
    }

    @Override
    protected StringBuilder appendDeleteClause(StringBuilder queryString) {
        // For delete we return SELECT * FROM ... WHERE to get documents and use API to delete them
        return queryString.append("SELECT * ").append(FROM_CLAUSE);
    }

    @Override
    protected boolean isAliasForBatch(PersistentEntity persistentEntity, AnnotationMetadata annotationMetadata) {
        return true;
    }

    @Override
    protected boolean computePropertyPaths() {
        return false;
    }

    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition definition) {
        return null;
    }

    @Override
    public QueryResult buildUpdate(AnnotationMetadata annotationMetadata, UpdateQueryDefinition definition) {
        QueryResult queryResult = super.buildUpdate(annotationMetadata, definition);
        String resultQuery = queryResult.getQuery();

        PersistentEntity entity = definition.persistentEntity();
        String tableAlias = getAliasName(entity);
        String tableName = getTableName(entity);

        final String finalQuery = "SELECT * FROM " + tableName + SPACE + tableAlias + SPACE +
            resultQuery.substring(resultQuery.toLowerCase(Locale.ROOT).indexOf("where"));
        StringJoiner stringJoiner = new StringJoiner(",");
        definition.propertiesToUpdate().keySet().forEach(stringJoiner::add);
        final String update = stringJoiner.toString();

        return new QueryResult() {

            @NonNull
            @Override
            public String getQuery() {
                return finalQuery;
            }

            @Override
            public String getUpdate() {
                return update;
            }

            @Override
            public List<String> getQueryParts() {
                return queryResult.getQueryParts();
            }

            @Override
            public List<QueryParameterBinding> getParameterBindings() {
                return queryResult.getParameterBindings();
            }

            @Override
            public Map<String, String> getAdditionalRequiredParameters() {
                return queryResult.getAdditionalRequiredParameters();
            }

        };
    }

    @Override
    public String buildLimitAndOffset(long limit, long offset) {
        if (limit > 0) {
            return " OFFSET " + offset + " LIMIT " + limit + " ";
        }
        return "";
    }
}
