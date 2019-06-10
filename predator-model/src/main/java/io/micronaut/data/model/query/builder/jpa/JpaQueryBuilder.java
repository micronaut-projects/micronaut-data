/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model.query.builder.jpa;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.JoinSpec;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.AssociationQuery;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.QueryBuilder;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds JPA 1.0 String-based queries from the Query model.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class JpaQueryBuilder implements QueryBuilder {
    private static final String DISTINCT_CLAUSE = "DISTINCT ";
    private static final String SELECT_CLAUSE = "SELECT ";
    private static final String AS_CLAUSE = " AS ";
    private static final String FROM_CLAUSE = " FROM ";
    private static final String ORDER_BY_CLAUSE = " ORDER BY ";
    private static final String WHERE_CLAUSE = " WHERE ";
    private static final char COMMA = ',';
    private static final char CLOSE_BRACKET = ')';
    private static final char OPEN_BRACKET = '(';
    private static final char SPACE = ' ';
    private static final char DOT = '.';
    private static final String NOT_CLAUSE = " NOT";
    private static final String LOGICAL_AND = " AND ";
    private static final String UPDATE_CLAUSE = "UPDATE ";
    private static final String DELETE_CLAUSE = "DELETE ";
    private static final String LOGICAL_OR = " OR ";
    private static final Map<Class, QueryHandler> QUERY_HANDLERS = new HashMap<>();

    @NonNull
    @Override
    public QueryResult buildQuery(@NonNull QueryModel query) {
        QueryState queryState = new QueryState(query, true);
        queryState.query.append(SELECT_CLAUSE);

        buildSelectClause(query, queryState);
        QueryModel.Junction criteria = query.getCriteria();

        Map<String, String> parameters = null;
        if (!criteria.isEmpty()) {
            parameters = buildWhereClause(criteria, queryState);
        }

        appendOrder(query, queryState);
        return QueryResult.of(queryState.query.toString(), parameters);
    }

    @NonNull
    @Override
    public QueryResult buildUpdate(@NonNull QueryModel query, List<String> propertiesToUpdate) {
        if (propertiesToUpdate.isEmpty()) {
            throw new IllegalArgumentException("No properties specified to update");
        }
        PersistentEntity entity = query.getPersistentEntity();
        QueryState queryState = new QueryState(query, false);
        queryState.query.append(UPDATE_CLAUSE)
                .append(entity.getName())
                .append(SPACE)
                .append(queryState.logicalName);
        buildUpdateStatement(queryState, propertiesToUpdate);
        buildWhereClause(query.getCriteria(), queryState);
        return QueryResult.of(queryState.query.toString(), queryState.parameters);
    }

    @NonNull
    @Override
    public QueryResult buildDelete(@NonNull QueryModel query) {
        PersistentEntity entity = query.getPersistentEntity();
        QueryState queryState = new QueryState(query, false);
        queryState.query.append(DELETE_CLAUSE).append(entity.getName()).append(SPACE).append(queryState.logicalName);
        buildWhereClause(query.getCriteria(), queryState);
        return QueryResult.of(queryState.query.toString(), queryState.parameters);
    }

    @NonNull
    @Override
    public QueryResult buildOrderBy(@NonNull PersistentEntity entity, @NonNull Sort sort) {
        ArgumentUtils.requireNonNull("entity", entity);
        ArgumentUtils.requireNonNull("sort", sort);
        List<Sort.Order> orders = sort.getOrderBy();
        if (CollectionUtils.isEmpty(orders)) {
            throw new IllegalArgumentException("Sort is empty");
        }
        for (Sort.Order order : orders) {
            String property = order.getProperty();
            if (!entity.getPropertyByPath(property).isPresent()) {
                throw new IllegalArgumentException("Cannot sort on non-existent property path: " + property);
            }
        }

        StringBuilder buff = new StringBuilder(ORDER_BY_CLAUSE);
        Iterator<Sort.Order> i = orders.iterator();
        while (i.hasNext()) {
            Sort.Order order = i.next();
            buff.append(entity.getDecapitalizedName())
                    .append(DOT)
                    .append(order.getProperty())
                    .append(SPACE)
                    .append(order.getDirection().toString());
            if (i.hasNext()) {
                buff.append(",");
            }
        }

        return QueryResult.of(buff.toString(), Collections.emptyMap());
    }

    private void buildSelectClause(QueryModel query, QueryState queryState) {
        String logicalName = queryState.logicalName;
        PersistentEntity entity = queryState.entity;
        StringBuilder queryString = queryState.query;
        buildSelect(queryString, query.getProjections(), logicalName, entity);

        queryString.append(FROM_CLAUSE)
                .append(entity.getName())
                .append(AS_CLAUSE)
                .append(logicalName);
    }

    private static void buildSelect(StringBuilder queryString, List<QueryModel.Projection> projectionList, String logicalName, PersistentEntity entity) {
        if (projectionList.isEmpty()) {
            queryString.append(logicalName);
        } else {
            for (Iterator i = projectionList.iterator(); i.hasNext();) {
                QueryModel.Projection projection = (QueryModel.Projection) i.next();
                if (projection instanceof QueryModel.CountProjection) {
                    queryString.append("COUNT(")
                            .append(logicalName)
                            .append(CLOSE_BRACKET);
                } else if (projection instanceof QueryModel.DistinctProjection) {
                    queryString.append("DISTINCT(")
                            .append(logicalName)
                            .append(CLOSE_BRACKET);
                } else if (projection instanceof QueryModel.IdProjection) {
                    queryString.append(logicalName)
                            .append(DOT)
                            .append(entity.getIdentity().getName());
                } else if (projection instanceof QueryModel.PropertyProjection) {
                    QueryModel.PropertyProjection pp = (QueryModel.PropertyProjection) projection;
                    String alias = pp.getAlias().orElse(null);
                    if (projection instanceof QueryModel.AvgProjection) {
                        queryString.append("AVG(")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    } else if (projection instanceof QueryModel.DistinctPropertyProjection) {
                        queryString.append("DISTINCT(")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    } else if (projection instanceof QueryModel.SumProjection) {
                        queryString.append("SUM(")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    } else if (projection instanceof QueryModel.MinProjection) {
                        queryString.append("MIN(")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    } else if (projection instanceof QueryModel.MaxProjection) {
                        queryString.append("MAX(")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    } else if (projection instanceof QueryModel.CountDistinctProjection) {
                        queryString.append("COUNT(DISTINCT ")
                                .append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName())
                                .append(CLOSE_BRACKET);
                    } else {
                        queryString.append(logicalName)
                                .append(DOT)
                                .append(pp.getPropertyName());
                    }
                    if (alias != null) {
                        queryString.append(AS_CLAUSE)
                                   .append(alias);
                    }
                }

                if (i.hasNext()) {
                    queryString.append(COMMA);
                }
            }
        }
    }

    private static void appendCriteriaForOperator(QueryState queryState,
                                                  final String name,
                                                  Object value,
                                                  String operator) {
        String parameterName = newParameter(queryState.position);
        queryState.whereClause.append(queryState.logicalName)
                            .append(DOT)
                            .append(name)
                            .append(operator)
                            .append(':')
                            .append(parameterName);
        if (value instanceof QueryParameter) {
            queryState.parameters.put(parameterName, ((QueryParameter) value).getName());
        }
    }

    private static String newParameter(AtomicInteger position) {
        return "p" + position.incrementAndGet();
    }

    static {

        QUERY_HANDLERS.put(AssociationQuery.class, (queryState, criterion) -> {

            if (!queryState.allowJoins) {
                throw new IllegalArgumentException("Joins cannot be used in a DELETE or UPDATE operation");
            }
            AssociationQuery aq = (AssociationQuery) criterion;
            final Association association = aq.getAssociation();
            QueryModel.Junction associationCriteria = aq.getCriteria();
            List<QueryModel.Criterion> associationCriteriaList = associationCriteria.getCriteria();

            handleAssociationCriteria(
                    queryState, association, associationCriteria, associationCriteriaList
            );
        });

        QUERY_HANDLERS.put(QueryModel.Negation.class, (queryState, criterion) -> {

            queryState.whereClause.append(NOT_CLAUSE)
                                  .append(OPEN_BRACKET);

            final QueryModel.Negation negation = (QueryModel.Negation) criterion;
            buildWhereClauseForCriterion(
                    queryState,
                    negation,
                    negation.getCriteria()
            );
            queryState.whereClause.append(CLOSE_BRACKET);
        });

        QUERY_HANDLERS.put(QueryModel.Conjunction.class, (queryState, criterion) -> {
            queryState.whereClause.append(OPEN_BRACKET);

            final QueryModel.Conjunction conjunction = (QueryModel.Conjunction) criterion;
            buildWhereClauseForCriterion(queryState, conjunction, conjunction.getCriteria());
            queryState.whereClause.append(CLOSE_BRACKET);
        });

        QUERY_HANDLERS.put(QueryModel.Disjunction.class, (queryState, criterion) -> {
            queryState.whereClause.append(OPEN_BRACKET);

            final QueryModel.Disjunction disjunction = (QueryModel.Disjunction) criterion;
            buildWhereClauseForCriterion(queryState, disjunction, disjunction.getCriteria());
            queryState.whereClause.append(CLOSE_BRACKET);
        });

        QUERY_HANDLERS.put(QueryModel.Equals.class, (queryState, criterion) -> {
            QueryModel.Equals eq = (QueryModel.Equals) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.Equals.class);
            if (eq.isIgnoreCase()) {
                appendCaseInsensitiveCriterion(
                        queryState,
                        eq,
                        prop,
                        "="
                );
            } else {
                appendCriteriaForOperator(
                        queryState,
                        name.indexOf('.') == -1 ? prop.getName() : name,
                        eq.getValue(),
                        " = "
                );
            }
        });

        QUERY_HANDLERS.put(QueryModel.EqualsProperty.class, (queryState, criterion) -> {
            final PersistentEntity entity = queryState.entity;
            QueryModel.EqualsProperty eq = (QueryModel.EqualsProperty) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.EqualsProperty.class);
            validateProperty(entity, otherProperty, QueryModel.EqualsProperty.class);
            appendPropertyComparison(queryState.whereClause, queryState.logicalName, propertyName, otherProperty, "=");
        });

        QUERY_HANDLERS.put(QueryModel.NotEqualsProperty.class, (queryState, criterion) -> {
            final PersistentEntity entity = queryState.entity;
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.NotEqualsProperty.class);
            validateProperty(entity, otherProperty, QueryModel.NotEqualsProperty.class);
            appendPropertyComparison(queryState.whereClause, queryState.logicalName, propertyName, otherProperty, "!=");
        });

        QUERY_HANDLERS.put(QueryModel.GreaterThanProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.entity;
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.GreaterThanProperty.class);
            validateProperty(entity, otherProperty, QueryModel.GreaterThanProperty.class);
            appendPropertyComparison(queryState.whereClause, queryState.logicalName, propertyName, otherProperty, ">");
        });

        QUERY_HANDLERS.put(QueryModel.GreaterThanEqualsProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.entity;
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.GreaterThanEqualsProperty.class);
            validateProperty(entity, otherProperty, QueryModel.GreaterThanEqualsProperty.class);
            appendPropertyComparison(queryState.whereClause, queryState.logicalName, propertyName, otherProperty, ">=");
        });

        QUERY_HANDLERS.put(QueryModel.LessThanProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.entity;
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.LessThanProperty.class);
            validateProperty(entity, otherProperty, QueryModel.LessThanProperty.class);
            appendPropertyComparison(queryState.whereClause, queryState.logicalName, propertyName, otherProperty, "<");
        });

        QUERY_HANDLERS.put(QueryModel.LessThanEqualsProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.entity;
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.LessThanEqualsProperty.class);
            validateProperty(entity, otherProperty, QueryModel.LessThanEqualsProperty.class);
            appendPropertyComparison(queryState.whereClause, queryState.logicalName, propertyName, otherProperty, "<=");
        });

        QUERY_HANDLERS.put(QueryModel.IsNull.class, (queryState, criterion) -> {
            QueryModel.IsNull isNull = (QueryModel.IsNull) criterion;
            final String name = isNull.getProperty();
            validateProperty(queryState.entity, name, QueryModel.IsNull.class);
            queryState.whereClause.append(queryState.logicalName)
                    .append(DOT)
                    .append(name)
                    .append(" IS NULL ");
        });

        QUERY_HANDLERS.put(QueryModel.IsTrue.class, (queryState, criterion) -> {
            QueryModel.IsTrue isNull = (QueryModel.IsTrue) criterion;
            final String name = isNull.getProperty();
            validateProperty(queryState.entity, name, QueryModel.IsTrue.class);
            queryState.whereClause.append(queryState.logicalName)
                    .append(DOT)
                    .append(name)
                    .append(" = TRUE ");
        });

        QUERY_HANDLERS.put(QueryModel.IsFalse.class, (queryState, criterion) -> {
            QueryModel.IsFalse isNull = (QueryModel.IsFalse) criterion;
            final String name = isNull.getProperty();
            validateProperty(queryState.entity, name, QueryModel.IsTrue.class);
            queryState.whereClause.append(queryState.logicalName)
                    .append(DOT)
                    .append(name)
                    .append(" = FALSE ");
        });

        QUERY_HANDLERS.put(QueryModel.IsNotNull.class, (queryState, criterion) -> {
            QueryModel.IsNotNull isNotNull = (QueryModel.IsNotNull) criterion;
            final String name = isNotNull.getProperty();
            validateProperty(queryState.entity, name, QueryModel.IsNotNull.class);
            queryState.whereClause.append(queryState.logicalName)
                    .append(DOT)
                    .append(name)
                    .append(" IS NOT NULL ");
        });

        QUERY_HANDLERS.put(QueryModel.IsEmpty.class, (queryState, criterion) -> {
            QueryModel.IsEmpty isEmpty = (QueryModel.IsEmpty) criterion;
            final String name = isEmpty.getProperty();
            PersistentProperty property = validateProperty(queryState.entity, name, QueryModel.IsEmpty.class);
            if (property.isAssignable(CharSequence.class)) {
                String path = queryState.logicalName + DOT + name;
                queryState.whereClause.append(path)
                        .append(" IS NULL OR ")
                        .append(path)
                        .append(" = '' ");

            } else {
                queryState.whereClause.append(queryState.logicalName)
                        .append(DOT)
                        .append(name)
                        .append(" IS EMPTY ");
            }
        });

        QUERY_HANDLERS.put(QueryModel.IsNotEmpty.class, (queryState, criterion) -> {
            QueryModel.IsNotEmpty isNotEmpty = (QueryModel.IsNotEmpty) criterion;
            final String name = isNotEmpty.getProperty();
            PersistentProperty property = validateProperty(queryState.entity, name, QueryModel.IsNotEmpty.class);
            if (property.isAssignable(CharSequence.class)) {
                String path = queryState.logicalName + DOT + name;
                queryState.whereClause.append(path)
                        .append(" IS NOT NULL AND ")
                        .append(path)
                        .append(" <> '' ");
            } else {

                queryState.whereClause.append(queryState.logicalName)
                        .append(DOT)
                        .append(name)
                        .append(" IS NOT EMPTY ");
            }
        });

        QUERY_HANDLERS.put(QueryModel.IdEquals.class, (queryState, criterion) -> {
            PersistentProperty prop = queryState.entity.getIdentity();
            if (prop == null) {
                throw new IllegalStateException("No id found for name entity: " + queryState.entity.getIdentity());
            }
            appendCriteriaForOperator(
                    queryState,
                    prop.getName(),
                    ((QueryModel.IdEquals) criterion).getValue(),
                    " = "
            );
        });

        QUERY_HANDLERS.put(QueryModel.NotEquals.class, (queryState, criterion) -> {
            QueryModel.NotEquals eq = (QueryModel.NotEquals) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.NotEquals.class);
            if (eq.isIgnoreCase()) {
                appendCaseInsensitiveCriterion(
                        queryState,
                        eq,
                        prop,
                        "!="
                );
            } else {
                appendCriteriaForOperator(
                        queryState, prop.getName(), eq.getValue(), " != "
                );
            }
        });

        QUERY_HANDLERS.put(QueryModel.GreaterThan.class, (queryState, criterion) -> {
            QueryModel.GreaterThan eq = (QueryModel.GreaterThan) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.GreaterThan.class);
            appendCriteriaForOperator(
                    queryState, prop.getName(), eq.getValue(), " > "
            );
        });

        QUERY_HANDLERS.put(QueryModel.LessThanEquals.class, (queryState, criterion) -> {
            QueryModel.LessThanEquals eq = (QueryModel.LessThanEquals) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.LessThanEquals.class);
            appendCriteriaForOperator(
                    queryState, prop.getName(), eq.getValue(), " <= "
            );
        });

        QUERY_HANDLERS.put(QueryModel.GreaterThanEquals.class, (queryState, criterion) -> {
            QueryModel.GreaterThanEquals eq = (QueryModel.GreaterThanEquals) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.GreaterThanEquals.class);
            appendCriteriaForOperator(
                    queryState, prop.getName(), eq.getValue(), " >= "
            );
        });

        QUERY_HANDLERS.put(QueryModel.Between.class, (queryState, criterion) -> {
            QueryModel.Between between = (QueryModel.Between) criterion;
            final String name = between.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.Between.class);
            final String qualifiedName = queryState.logicalName + DOT + name;
            String fromParam = newParameter(queryState.position);
            String toParam = newParameter(queryState.position);
            queryState.whereClause.append(OPEN_BRACKET)
                    .append(qualifiedName)
                    .append(" >= ")
                    .append(':')
                    .append(fromParam);
            queryState.whereClause.append(" AND ")
                    .append(qualifiedName)
                    .append(" <= ")
                    .append(':')
                    .append(toParam)
                    .append(CLOSE_BRACKET);

            queryState.parameters.put(fromParam, between.getFrom().getName());
            queryState.parameters.put(toParam, between.getTo().getName());
        });

        QUERY_HANDLERS.put(QueryModel.LessThan.class, (queryState, criterion) -> {
            QueryModel.LessThan eq = (QueryModel.LessThan) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.LessThan.class);
            appendCriteriaForOperator(
                    queryState, prop.getName(), eq.getValue(), " < "
            );
        });

        QUERY_HANDLERS.put(QueryModel.Like.class, (queryState, criterion) -> {
            QueryModel.Like eq = (QueryModel.Like) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.Like.class);
            appendCriteriaForOperator(
                    queryState, prop.getName(), eq.getValue(), " like "
            );
        });

        QUERY_HANDLERS.put(QueryModel.ILike.class, (queryState, criterion) -> {
            QueryModel.ILike eq = (QueryModel.ILike) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.ILike.class);
            String operator = "like";
            appendCaseInsensitiveCriterion(queryState, eq, prop, operator);
        });

        QUERY_HANDLERS.put(QueryModel.StartsWith.class, (queryState, criterion) -> {
            QueryModel.StartsWith eq = (QueryModel.StartsWith) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.ILike.class);
            String parameterName = newParameter(queryState.position);
            queryState.whereClause
                    .append(queryState.logicalName)
                    .append(DOT)
                    .append(prop.getName())
                    .append(" LIKE CONCAT(")
                    .append(':')
                    .append(parameterName)
                    .append(",'%')");
            Object value = eq.getValue();
            if (value instanceof QueryParameter) {
                queryState.parameters.put(parameterName, ((QueryParameter) value).getName());
            }
        });

        QUERY_HANDLERS.put(QueryModel.Contains.class, (queryState, criterion) -> {
            QueryModel.Contains eq = (QueryModel.Contains) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.ILike.class);
            String parameterName = newParameter(queryState.position);
            queryState.whereClause
                    .append(queryState.logicalName)
                    .append(DOT)
                    .append(prop.getName())
                    .append(" LIKE CONCAT('%',")
                    .append(':')
                    .append(parameterName)
                    .append(",'%')");
            Object value = eq.getValue();
            if (value instanceof QueryParameter) {
                queryState.parameters.put(parameterName, ((QueryParameter) value).getName());
            }
        });

        QUERY_HANDLERS.put(QueryModel.EndsWith.class, (queryState, criterion) -> {
            QueryModel.EndsWith eq = (QueryModel.EndsWith) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.ILike.class);
            String parameterName = newParameter(queryState.position);
            queryState.whereClause
                    .append(queryState.logicalName)
                    .append(DOT)
                    .append(prop.getName())
                    .append(" LIKE CONCAT('%',")
                    .append(':')
                    .append(parameterName)
                    .append(")");
            Object value = eq.getValue();
            if (value instanceof QueryParameter) {
                queryState.parameters.put(parameterName, ((QueryParameter) value).getName());
            }
        });

        QUERY_HANDLERS.put(QueryModel.In.class, (queryState, criterion) -> {
            QueryModel.In inQuery = (QueryModel.In) criterion;
            final String name = inQuery.getProperty();
            PersistentProperty prop = validateProperty(queryState.entity, name, QueryModel.In.class);
            queryState.whereClause.append(queryState.logicalName)
                    .append(DOT)
                    .append(name)
                    .append(" IN (");
            QueryModel subquery = inQuery.getSubquery();
            if (subquery != null) {
                buildSubQuery(queryState, subquery);
            } else {
                String parameterName = newParameter(queryState.position);
                queryState.whereClause.append(':').append(parameterName);
                Object value = inQuery.getValue();
                if (value instanceof QueryParameter) {
                    queryState.parameters.put(parameterName, ((QueryParameter) value).getName());
                }
            }
            queryState.whereClause.append(CLOSE_BRACKET);

        });

        QUERY_HANDLERS.put(QueryModel.NotIn.class, (queryState, criterion) -> {
            String comparisonExpression = " NOT IN (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        QUERY_HANDLERS.put(QueryModel.EqualsAll.class, (queryState, criterion) -> {
            String comparisonExpression = " = ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        QUERY_HANDLERS.put(QueryModel.NotEqualsAll.class, (queryState, criterion) -> {
            String comparisonExpression = " != ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        QUERY_HANDLERS.put(QueryModel.GreaterThanAll.class, (queryState, criterion) -> {
            String comparisonExpression = " > ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        QUERY_HANDLERS.put(QueryModel.GreaterThanSome.class, (queryState, criterion) -> {
            String comparisonExpression = " > SOME (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        QUERY_HANDLERS.put(QueryModel.GreaterThanEqualsAll.class, (queryState, criterion) -> {
            String comparisonExpression = " >= ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        QUERY_HANDLERS.put(QueryModel.GreaterThanEqualsSome.class, (queryState, criterion) -> {
            String comparisonExpression = " >= SOME (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        QUERY_HANDLERS.put(QueryModel.LessThanAll.class, (queryState, criterion) -> {
            String comparisonExpression = " < ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        QUERY_HANDLERS.put(QueryModel.LessThanSome.class, (queryState, criterion) -> {
            String comparisonExpression = " < SOME (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        QUERY_HANDLERS.put(QueryModel.LessThanEqualsAll.class, (queryState, criterion) -> {
            String comparisonExpression = " <= ALL (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

        QUERY_HANDLERS.put(QueryModel.LessThanEqualsSome.class, (queryState, criterion) -> {
            String comparisonExpression = " <= SOME (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });

    }

    private static void appendCaseInsensitiveCriterion(QueryState queryState, QueryModel.PropertyCriterion criterion, PersistentProperty prop, String operator) {
        String parameterName = newParameter(queryState.position);
        queryState.whereClause.append("lower(")
                .append(queryState.logicalName)
                .append(DOT)
                .append(prop.getName())
                .append(") ")
                .append(operator)
                .append(" lower(")
                .append(':')
                .append(parameterName)
                .append(")");
        Object value = criterion.getValue();
        if (value instanceof QueryParameter) {
            queryState.parameters.put(parameterName, ((QueryParameter) value).getName());
        }
    }

    private static void handleSubQuery(QueryState queryState, QueryModel.SubqueryCriterion subqueryCriterion, String comparisonExpression) {
        final String name = subqueryCriterion.getProperty();
        validateProperty(queryState.entity, name, QueryModel.In.class);
        QueryModel subquery = subqueryCriterion.getValue();
        queryState.whereClause.append(queryState.logicalName)
                .append(DOT)
                .append(name)
                .append(comparisonExpression);
        buildSubQuery(queryState, subquery);
        queryState.whereClause.append(CLOSE_BRACKET);
    }

    private static void buildSubQuery(QueryState queryState, QueryModel subquery) {
        PersistentEntity associatedEntity = subquery.getPersistentEntity();
        String associatedEntityName = associatedEntity.getName();
        String associatedEntityLogicalName = associatedEntity.getDecapitalizedName() + queryState.position.incrementAndGet();
        queryState.whereClause.append("SELECT ");
        buildSelect(queryState.whereClause, subquery.getProjections(), associatedEntityLogicalName, associatedEntity);
        queryState.whereClause.append(" FROM ")
                .append(associatedEntityName)
                .append(' ')
                .append(associatedEntityLogicalName)
                .append(" WHERE ");
        List<QueryModel.Criterion> criteria = subquery.getCriteria().getCriteria();
        for (QueryModel.Criterion subCriteria : criteria) {
            QueryHandler queryHandler = QUERY_HANDLERS.get(subCriteria.getClass());
            if (queryHandler != null) {
                queryHandler.handle(
                        queryState,
                        subCriteria
                );
            }
        }
    }

    private static void handleAssociationCriteria(
            QueryState queryState,
            Association association,
            QueryModel.Junction associationCriteria,
            List<QueryModel.Criterion> associationCriteriaList) {
        if (association == null) {
            return;
        }
        String currentName = queryState.logicalName;
        PersistentEntity currentEntity = queryState.entity;
        final PersistentEntity associatedEntity = association.getAssociatedEntity();
        if (association.getKind() == Relation.Kind.ONE_TO_ONE) {
            final String associationName = association.getName();
            try {
                queryState.entity = associatedEntity;
                queryState.logicalName = currentName + DOT + associationName;
                buildWhereClauseForCriterion(
                        queryState,
                        associationCriteria,
                        associationCriteriaList
                );
            } finally {
                queryState.logicalName = currentName;
                queryState.entity = currentEntity;
            }
        }

        final String associationName = association.getName();
        String associationPath = queryState.logicalName + DOT + associationName;
        if (!queryState.appliedJoinPaths.contains(associationPath)) {
            queryState.appliedJoinPaths.add(associationPath);
            JoinSpec.Type jt = queryState.queryObject.getJoinType(association).orElse(JoinSpec.Type.DEFAULT);
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
                    joinType = " JOIN FETCH ";
                    break;
                case FETCH:
                default:
                    joinType = " JOIN ";
            }


            queryState.query.append(joinType)
                    .append(queryState.logicalName)
                    .append(DOT)
                    .append(associationName)
                    .append(SPACE)
                    .append(associationName);
        }

        try {
            queryState.entity = associatedEntity;
            queryState.logicalName = associationName;
            buildWhereClauseForCriterion(
                    queryState,
                    associationCriteria,
                    associationCriteriaList
            );
        } finally {
            queryState.logicalName = currentName;
            queryState.entity = currentEntity;
        }

    }

    private void buildUpdateStatement(
            QueryState queryState,
            List<String> propertiesToUpdate) {
        StringBuilder queryString = queryState.query;
        Map<String, String> parameters = queryState.parameters;
        queryString.append(SPACE).append("SET");

        // keys need to be sorted before query is built

        Iterator<String> iterator = propertiesToUpdate.iterator();
        while (iterator.hasNext()) {
            String propertyName = iterator.next();
            PersistentProperty prop = queryState.entity.getPropertyByName(propertyName);
            if (prop == null) {
                continue;
            }

            queryString.append(SPACE).append(queryState.logicalName).append(DOT).append(propertyName).append('=');
            String param = newParameter(queryState.position);
            queryString.append(':').append(param);
            parameters.put(param, prop.getName());
            if (iterator.hasNext()) {
                queryString.append(COMMA);
            }
        }
    }

    private static void appendPropertyComparison(StringBuilder q, String logicalName, String propertyName, String otherProperty, String operator) {
        q.append(logicalName)
                .append(DOT)
                .append(propertyName)
                .append(operator)
                .append(logicalName)
                .append(DOT)
                .append(otherProperty);
    }

    private static PersistentProperty validateProperty(PersistentEntity entity, String name, Class criterionType) {
        PersistentProperty identity = entity.getIdentity();
        if (identity != null && identity.getName().equals(name)) {
            return identity;
        }
        PersistentProperty[] compositeIdentity = entity.getCompositeIdentity();
        if (compositeIdentity != null) {
            for (PersistentProperty property : compositeIdentity) {
                if (property.getName().equals(name)) {
                    return property;
                }
            }
        }
        PersistentProperty prop = entity.getPropertyByPath(name).orElse(null);
        if (prop == null) {

            if (name.equals("id")) {
                // special case handling for ID
                if (identity != null) {
                    return identity;
                }
            } else {
                throw new IllegalArgumentException("Cannot use [" +
                        criterionType.getSimpleName() + "] criterion on non-existent property: " + name);
            }
        }
        return prop;
    }

    private Map<String, String> buildWhereClause(
            QueryModel.Junction criteria,
            QueryState queryState) {
        if (!criteria.isEmpty()) {

            final List<QueryModel.Criterion> criterionList = criteria.getCriteria();
            StringBuilder whereClause = queryState.whereClause;
            whereClause.append(WHERE_CLAUSE);
            if (criteria instanceof QueryModel.Negation) {
                whereClause.append(NOT_CLAUSE);
            }
            whereClause.append(OPEN_BRACKET);
            buildWhereClauseForCriterion(queryState, criteria, criterionList);
            String whereStr = whereClause.toString();
            if (!whereStr.equals(WHERE_CLAUSE + OPEN_BRACKET)) {
                queryState.query.append(whereStr);
                queryState.query.append(CLOSE_BRACKET);
            }
        }
        return queryState.parameters;
    }

    private void appendOrder(QueryModel query, QueryState queryState) {
        List<Sort.Order> orders = query.getSort().getOrderBy();
        if (!orders.isEmpty()) {

            StringBuilder buff = queryState.query;
            buff.append(ORDER_BY_CLAUSE);
            Iterator<Sort.Order> i = orders.iterator();
            while (i.hasNext()) {
                Sort.Order order = i.next();
                buff.append(queryState.logicalName)
                        .append(DOT)
                        .append(order.getProperty())
                        .append(SPACE)
                        .append(order.getDirection().toString());
                if (i.hasNext()) {
                    buff.append(",");
                }
            }
        }
    }

    private static void buildWhereClauseForCriterion(
            final QueryState queryState,
            QueryModel.Junction criteria,
            final List<QueryModel.Criterion> criterionList) {
        boolean isFirst = true;
        for (QueryModel.Criterion criterion : criterionList) {
            final String operator = criteria instanceof QueryModel.Conjunction ? LOGICAL_AND : LOGICAL_OR;
            QueryHandler qh = QUERY_HANDLERS.get(criterion.getClass());
            boolean isAssociationCriteria = criterion instanceof AssociationQuery;
            if (qh != null) {
                if (!isFirst) {
                    if (isAssociationCriteria) {
                        if (!((AssociationQuery) criterion).getCriteria().getCriteria().isEmpty()) {
                            queryState.whereClause.append(operator);
                        }
                    } else {
                        queryState.whereClause.append(operator);
                    }

                }

                qh.handle(queryState, criterion);
            } else {
                if (isAssociationCriteria) {

                    if (!queryState.allowJoins) {
                        throw new IllegalArgumentException("Joins cannot be used in a DELETE or UPDATE operation");
                    }
                    AssociationQuery ac = (AssociationQuery) criterion;
                    Association association = ac.getAssociation();
                    QueryModel.Junction junction = ac.getCriteria();
                    handleAssociationCriteria(
                            queryState,
                            association,
                            junction,
                            junction.getCriteria()
                    );
                } else {
                    throw new IllegalArgumentException("Queries of type " + criterion.getClass().getSimpleName() + " are not supported by this implementation");
                }
            }

            if (isFirst) {
                if (isAssociationCriteria) {
                    if (!((AssociationQuery) criterion).getCriteria().getCriteria().isEmpty()) {
                        isFirst = false;
                    }
                } else {
                    isFirst = false;
                }
            }
        }

    }

    /**
     * A query handler.
     */
    private interface QueryHandler {
        void handle(QueryState queryState, QueryModel.Criterion criterion);
    }

    /**
     * The state of the query.
     */
    private class QueryState {
        final Set<String> appliedJoinPaths = new HashSet<>();
        final AtomicInteger position = new AtomicInteger(0);
        final Map<String, String> parameters  = new LinkedHashMap<>();
        final StringBuilder query = new StringBuilder();
        final StringBuilder whereClause = new StringBuilder();
        final boolean allowJoins;
        final QueryModel queryObject;
        String logicalName;
        PersistentEntity entity;

        QueryState(QueryModel query, boolean allowJoins) {
            this.allowJoins = allowJoins;
            this.queryObject = query;
            this.entity = query.getPersistentEntity();
            this.logicalName = entity.getDecapitalizedName();
        }
    }
}
