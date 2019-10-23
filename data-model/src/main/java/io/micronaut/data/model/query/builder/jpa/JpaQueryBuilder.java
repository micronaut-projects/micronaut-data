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
package io.micronaut.data.model.query.builder.jpa;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;

import java.util.Map;

/**
 * Builds JPA 1.0 String-based queries from the Query model.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class JpaQueryBuilder extends AbstractSqlLikeQueryBuilder implements QueryBuilder {
    /**
     * Default constructor.
     */
    public JpaQueryBuilder() {
        queryHandlers.put(QueryModel.EqualsAll.class, (queryState, criterion) -> {
            String comparisonExpression = " = ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        queryHandlers.put(QueryModel.NotEqualsAll.class, (queryState, criterion) -> {
            String comparisonExpression = " != ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        queryHandlers.put(QueryModel.GreaterThanAll.class, (queryState, criterion) -> {
            String comparisonExpression = " > ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        queryHandlers.put(QueryModel.GreaterThanSome.class, (queryState, criterion) -> {
            String comparisonExpression = " > SOME (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        queryHandlers.put(QueryModel.GreaterThanEqualsAll.class, (queryState, criterion) -> {
            String comparisonExpression = " >= ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        queryHandlers.put(QueryModel.GreaterThanEqualsSome.class, (queryState, criterion) -> {
            String comparisonExpression = " >= SOME (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        queryHandlers.put(QueryModel.LessThanAll.class, (queryState, criterion) -> {
            String comparisonExpression = " < ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        queryHandlers.put(QueryModel.LessThanSome.class, (queryState, criterion) -> {
            String comparisonExpression = " < SOME (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        queryHandlers.put(QueryModel.LessThanEqualsAll.class, (queryState, criterion) -> {
            String comparisonExpression = " <= ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        queryHandlers.put(QueryModel.LessThanEqualsSome.class, (queryState, criterion) -> {
            String comparisonExpression = " <= SOME (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });
    }

    @Override
    protected String quote(String persistedName) {
        return persistedName;
    }

    @Override
    public String getAliasName(PersistentEntity entity) {
        return entity.getDecapitalizedName() + "_";
    }

    @Override
    protected String[] buildJoin(String alias, JoinPath joinPath, String joinType, StringBuilder target, Map<String, String> appliedJoinPaths) {
        Association association = joinPath.getAssociation();
        String joinAlias = getAliasName(joinPath);
        target.append(joinType)
                .append(alias)
                .append(DOT)
                .append(association.getName())
                .append(SPACE)
                .append(joinAlias);
        return new String[] { joinAlias };
    }

    @Override
    protected String getTableName(PersistentEntity entity) {
        return entity.getName();
    }

    @Override
    protected String getColumnName(PersistentProperty persistentProperty) {
        return persistentProperty.getName();
    }

    @Override
    protected void selectAllColumns(QueryState queryState) {
        queryState.getQuery().append(queryState.getCurrentAlias());
    }

    @Override
    protected void appendProjectionRowCount(StringBuilder queryString, String logicalName) {
        queryString.append(FUNCTION_COUNT)
                .append(OPEN_BRACKET)
                .append(logicalName)
                .append(CLOSE_BRACKET);
    }

    @Override
    protected final boolean computePropertyPaths() {
        return false;
    }

    @Override
    protected boolean isAliasForBatch() {
        return true;
    }

    @Override
    protected Placeholder formatParameter(int index) {
        String n = "p" + index;
        return new Placeholder(":" + n, n);
    }

    @Override
    public String resolveJoinType(Join.Type jt) {
        String joinType;
        switch (jt) {
            case LEFT:
                joinType = " LEFT JOIN ";
                break;
            case LEFT_FETCH:
                joinType = " LEFT JOIN FETCH ";
                break;
            case RIGHT:
                joinType = " RIGHT JOIN ";
                break;
            case RIGHT_FETCH:
                joinType = " RIGHT JOIN FETCH ";
                break;
            case INNER:
            case FETCH:
                joinType = " JOIN FETCH ";
                break;
            default:
                joinType = " JOIN ";
        }
        return joinType;
    }

    @Nullable
    @Override
    public QueryResult buildInsert(AnnotationMetadata repositoryMetadata, PersistentEntity entity) {
        // JPA doesn't require an insert statement
        return null;
    }

    @NonNull
    @Override
    protected StringBuilder appendDeleteClause(StringBuilder queryString) {
        return queryString.append(DELETE_CLAUSE);
    }

    @NonNull
    @Override
    public QueryResult buildPagination(@NonNull Pageable pageable) {
        throw new UnsupportedOperationException("JPA-QL does not support pagination in query definitions");
    }
}
