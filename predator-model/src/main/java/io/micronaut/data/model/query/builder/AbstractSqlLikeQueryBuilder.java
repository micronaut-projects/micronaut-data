package io.micronaut.data.model.query.builder;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.AssociationQuery;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An abstract class for builders that build SQL-like queries.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public abstract class AbstractSqlLikeQueryBuilder implements QueryBuilder {
    protected static final String SELECT_CLAUSE = "SELECT ";
    protected static final String AS_CLAUSE = " AS ";
    protected static final String FROM_CLAUSE = " FROM ";
    protected static final String ORDER_BY_CLAUSE = " ORDER BY ";
    protected static final String WHERE_CLAUSE = " WHERE ";
    protected static final char COMMA = ',';
    protected static final char CLOSE_BRACKET = ')';
    protected static final char OPEN_BRACKET = '(';
    protected static final char SPACE = ' ';
    protected static final char DOT = '.';
    protected static final String NOT_CLAUSE = " NOT";
    protected static final String LOGICAL_AND = " AND ";
    protected static final String UPDATE_CLAUSE = "UPDATE ";
    protected static final String DELETE_CLAUSE = "DELETE ";
    protected static final String LOGICAL_OR = " OR ";
    protected static final String FUNCTION_COUNT = "COUNT";
    private static final String DISTINCT_CLAUSE = "DISTINCT ";
    protected final Map<Class, QueryHandler> queryHandlers = new HashMap<>(30);

    {

        queryHandlers.put(AssociationQuery.class, (queryState, criterion) -> {

            if (!queryState.isAllowJoins()) {
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

        queryHandlers.put(QueryModel.Negation.class, (queryState, criterion) -> {

            queryState.getWhereClause().append(NOT_CLAUSE)
                    .append(OPEN_BRACKET);

            final QueryModel.Negation negation = (QueryModel.Negation) criterion;
            buildWhereClauseForCriterion(
                    queryState,
                    negation,
                    negation.getCriteria()
            );
            queryState.getWhereClause().append(CLOSE_BRACKET);
        });

        queryHandlers.put(QueryModel.Conjunction.class, (queryState, criterion) -> {
            queryState.getWhereClause().append(OPEN_BRACKET);

            final QueryModel.Conjunction conjunction = (QueryModel.Conjunction) criterion;
            buildWhereClauseForCriterion(queryState, conjunction, conjunction.getCriteria());
            queryState.getWhereClause().append(CLOSE_BRACKET);
        });

        queryHandlers.put(QueryModel.Disjunction.class, (queryState, criterion) -> {
            queryState.getWhereClause().append(OPEN_BRACKET);

            final QueryModel.Disjunction disjunction = (QueryModel.Disjunction) criterion;
            buildWhereClauseForCriterion(queryState, disjunction, disjunction.getCriteria());
            queryState.getWhereClause().append(CLOSE_BRACKET);
        });

        queryHandlers.put(QueryModel.Equals.class, (queryState, criterion) -> {
            QueryModel.Equals eq = (QueryModel.Equals) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.Equals.class);
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
                        prop,
                        name,
                        eq.getValue(),
                        " = "
                );
            }
        });

        queryHandlers.put(QueryModel.EqualsProperty.class, (queryState, criterion) -> {
            final PersistentEntity entity = queryState.getEntity();
            QueryModel.EqualsProperty eq = (QueryModel.EqualsProperty) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.EqualsProperty.class);
            validateProperty(entity, otherProperty, QueryModel.EqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getLogicalName(), propertyName, otherProperty, "=");
        });

        queryHandlers.put(QueryModel.NotEqualsProperty.class, (queryState, criterion) -> {
            final PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.NotEqualsProperty.class);
            validateProperty(entity, otherProperty, QueryModel.NotEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getLogicalName(), propertyName, otherProperty, "!=");
        });

        queryHandlers.put(QueryModel.GreaterThanProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.GreaterThanProperty.class);
            validateProperty(entity, otherProperty, QueryModel.GreaterThanProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getLogicalName(), propertyName, otherProperty, ">");
        });

        queryHandlers.put(QueryModel.GreaterThanEqualsProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.GreaterThanEqualsProperty.class);
            validateProperty(entity, otherProperty, QueryModel.GreaterThanEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getLogicalName(), propertyName, otherProperty, ">=");
        });

        queryHandlers.put(QueryModel.LessThanProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.LessThanProperty.class);
            validateProperty(entity, otherProperty, QueryModel.LessThanProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getLogicalName(), propertyName, otherProperty, "<");
        });

        queryHandlers.put(QueryModel.LessThanEqualsProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            validateProperty(entity, propertyName, QueryModel.LessThanEqualsProperty.class);
            validateProperty(entity, otherProperty, QueryModel.LessThanEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getLogicalName(), propertyName, otherProperty, "<=");
        });

        queryHandlers.put(QueryModel.IsNull.class, (queryState, criterion) -> {
            QueryModel.IsNull isNull = (QueryModel.IsNull) criterion;
            final String name = isNull.getProperty();
            validateProperty(queryState.getEntity(), name, QueryModel.IsNull.class);
            queryState.getWhereClause().append(queryState.getLogicalName())
                    .append(DOT)
                    .append(name)
                    .append(" IS NULL ");
        });

        queryHandlers.put(QueryModel.IsTrue.class, (queryState, criterion) -> {
            QueryModel.IsTrue isNull = (QueryModel.IsTrue) criterion;
            final String name = isNull.getProperty();
            validateProperty(queryState.getEntity(), name, QueryModel.IsTrue.class);
            queryState.getWhereClause().append(queryState.getLogicalName())
                    .append(DOT)
                    .append(name)
                    .append(" = TRUE ");
        });

        queryHandlers.put(QueryModel.IsFalse.class, (queryState, criterion) -> {
            QueryModel.IsFalse isNull = (QueryModel.IsFalse) criterion;
            final String name = isNull.getProperty();
            validateProperty(queryState.getEntity(), name, QueryModel.IsTrue.class);
            queryState.getWhereClause().append(queryState.getLogicalName())
                    .append(DOT)
                    .append(name)
                    .append(" = FALSE ");
        });

        queryHandlers.put(QueryModel.IsNotNull.class, (queryState, criterion) -> {
            QueryModel.IsNotNull isNotNull = (QueryModel.IsNotNull) criterion;
            final String name = isNotNull.getProperty();
            validateProperty(queryState.getEntity(), name, QueryModel.IsNotNull.class);
            queryState.getWhereClause().append(queryState.getLogicalName())
                    .append(DOT)
                    .append(name)
                    .append(" IS NOT NULL ");
        });

        queryHandlers.put(QueryModel.IsEmpty.class, (queryState, criterion) -> {
            QueryModel.IsEmpty isEmpty = (QueryModel.IsEmpty) criterion;
            final String name = isEmpty.getProperty();
            PersistentProperty property = validateProperty(queryState.getEntity(), name, QueryModel.IsEmpty.class);
            if (property.isAssignable(CharSequence.class)) {
                String path = queryState.getLogicalName() + DOT + name;
                queryState.getWhereClause().append(path)
                        .append(" IS NULL OR ")
                        .append(path)
                        .append(" = '' ");

            } else {
                queryState.getWhereClause().append(queryState.getLogicalName())
                        .append(DOT)
                        .append(name)
                        .append(" IS EMPTY ");
            }
        });

        queryHandlers.put(QueryModel.IsNotEmpty.class, (queryState, criterion) -> {
            QueryModel.IsNotEmpty isNotEmpty = (QueryModel.IsNotEmpty) criterion;
            final String name = isNotEmpty.getProperty();
            PersistentProperty property = validateProperty(queryState.getEntity(), name, QueryModel.IsNotEmpty.class);
            if (property.isAssignable(CharSequence.class)) {
                String path = queryState.getLogicalName() + DOT + name;
                queryState.getWhereClause().append(path)
                        .append(" IS NOT NULL AND ")
                        .append(path)
                        .append(" <> '' ");
            } else {

                queryState.getWhereClause().append(queryState.getLogicalName())
                        .append(DOT)
                        .append(name)
                        .append(" IS NOT EMPTY ");
            }
        });

        queryHandlers.put(QueryModel.IdEquals.class, (queryState, criterion) -> {
            PersistentProperty prop = queryState.getEntity().getIdentity();
            if (prop == null) {
                throw new IllegalStateException("No id found for name entity: " + queryState.getEntity().getIdentity());
            }
            appendCriteriaForOperator(
                    queryState,
                    prop,
                    prop.getName(),
                    ((QueryModel.IdEquals) criterion).getValue(),
                    " = "
            );
        });

        queryHandlers.put(QueryModel.NotEquals.class, (queryState, criterion) -> {
            QueryModel.NotEquals eq = (QueryModel.NotEquals) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.NotEquals.class);
            if (eq.isIgnoreCase()) {
                appendCaseInsensitiveCriterion(
                        queryState,
                        eq,
                        prop,
                        "!="
                );
            } else {
                appendCriteriaForOperator(
                        queryState, prop, name, eq.getValue(), " != "
                );
            }
        });

        queryHandlers.put(QueryModel.GreaterThan.class, (queryState, criterion) -> {
            QueryModel.GreaterThan eq = (QueryModel.GreaterThan) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.GreaterThan.class);
            appendCriteriaForOperator(
                    queryState, prop, name, eq.getValue(), " > "
            );
        });

        queryHandlers.put(QueryModel.LessThanEquals.class, (queryState, criterion) -> {
            QueryModel.LessThanEquals eq = (QueryModel.LessThanEquals) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.LessThanEquals.class);
            appendCriteriaForOperator(
                    queryState, prop, name, eq.getValue(), " <= "
            );
        });

        queryHandlers.put(QueryModel.GreaterThanEquals.class, (queryState, criterion) -> {
            QueryModel.GreaterThanEquals eq = (QueryModel.GreaterThanEquals) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.GreaterThanEquals.class);
            appendCriteriaForOperator(
                    queryState, prop, name, eq.getValue(), " >= "
            );
        });

        queryHandlers.put(QueryModel.Between.class, (queryState, criterion) -> {
            QueryModel.Between between = (QueryModel.Between) criterion;
            final String name = between.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.Between.class);
            final String qualifiedName = queryState.getLogicalName() + DOT + name;
            String fromParam = newParameter(queryState.getParameterPosition());
            String toParam = newParameter(queryState.getParameterPosition());
            queryState.getWhereClause().append(OPEN_BRACKET)
                    .append(qualifiedName)
                    .append(" >= ")
                    .append(':')
                    .append(fromParam);
            queryState.getWhereClause().append(" AND ")
                    .append(qualifiedName)
                    .append(" <= ")
                    .append(':')
                    .append(toParam)
                    .append(CLOSE_BRACKET);

            queryState.getParameters().put(fromParam, between.getFrom().getName());
            queryState.getParameters().put(toParam, between.getTo().getName());
        });

        queryHandlers.put(QueryModel.LessThan.class, (queryState, criterion) -> {
            QueryModel.LessThan eq = (QueryModel.LessThan) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.LessThan.class);
            appendCriteriaForOperator(
                    queryState, prop, name, eq.getValue(), " < "
            );
        });

        queryHandlers.put(QueryModel.Like.class, (queryState, criterion) -> {
            QueryModel.Like eq = (QueryModel.Like) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.Like.class);
            appendCriteriaForOperator(
                    queryState, prop, name, eq.getValue(), " like "
            );
        });

        queryHandlers.put(QueryModel.ILike.class, (queryState, criterion) -> {
            QueryModel.ILike eq = (QueryModel.ILike) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.ILike.class);
            String operator = "like";
            appendCaseInsensitiveCriterion(queryState, eq, prop, operator);
        });

        queryHandlers.put(QueryModel.StartsWith.class, (queryState, criterion) -> {
            QueryModel.StartsWith eq = (QueryModel.StartsWith) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.ILike.class);
            String parameterName = newParameter(queryState.getParameterPosition());
            queryState.getWhereClause()
                    .append(queryState.getLogicalName())
                    .append(DOT)
                    .append(prop.getName())
                    .append(" LIKE CONCAT(")
                    .append(':')
                    .append(parameterName)
                    .append(",'%')");
            Object value = eq.getValue();
            if (value instanceof QueryParameter) {
                queryState.getParameters().put(parameterName, ((QueryParameter) value).getName());
            }
        });

        queryHandlers.put(QueryModel.Contains.class, (queryState, criterion) -> {
            QueryModel.Contains eq = (QueryModel.Contains) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.ILike.class);
            String parameterName = newParameter(queryState.getParameterPosition());
            queryState.getWhereClause()
                    .append(queryState.getLogicalName())
                    .append(DOT)
                    .append(prop.getName())
                    .append(" LIKE CONCAT('%',")
                    .append(':')
                    .append(parameterName)
                    .append(",'%')");
            Object value = eq.getValue();
            if (value instanceof QueryParameter) {
                queryState.getParameters().put(parameterName, ((QueryParameter) value).getName());
            }
        });

        queryHandlers.put(QueryModel.EndsWith.class, (queryState, criterion) -> {
            QueryModel.EndsWith eq = (QueryModel.EndsWith) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.ILike.class);
            String parameterName = newParameter(queryState.getParameterPosition());
            queryState.getWhereClause()
                    .append(queryState.getLogicalName())
                    .append(DOT)
                    .append(prop.getName())
                    .append(" LIKE CONCAT('%',")
                    .append(':')
                    .append(parameterName)
                    .append(")");
            Object value = eq.getValue();
            if (value instanceof QueryParameter) {
                queryState.getParameters().put(parameterName, ((QueryParameter) value).getName());
            }
        });

        queryHandlers.put(QueryModel.In.class, (queryState, criterion) -> {
            QueryModel.In inQuery = (QueryModel.In) criterion;
            final String name = inQuery.getProperty();
            PersistentProperty prop = validateProperty(queryState.getEntity(), name, QueryModel.In.class);
            queryState.getWhereClause().append(queryState.getLogicalName())
                    .append(DOT)
                    .append(name)
                    .append(" IN (");
            QueryModel subquery = inQuery.getSubquery();
            if (subquery != null) {
                buildSubQuery(queryState, subquery);
            } else {
                String parameterName = newParameter(queryState.getParameterPosition());
                queryState.getWhereClause().append(':').append(parameterName);
                Object value = inQuery.getValue();
                if (value instanceof QueryParameter) {
                    queryState.getParameters().put(parameterName, ((QueryParameter) value).getName());
                }
            }
            queryState.getWhereClause().append(CLOSE_BRACKET);

        });

        queryHandlers.put(QueryModel.NotIn.class, (queryState, criterion) -> {
            String comparisonExpression = " NOT IN (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });
    }

    @NonNull
    @Override
    public QueryResult buildQuery(@NonNull QueryModel query) {
        ArgumentUtils.requireNonNull("query", query);
        QueryState queryState = newQueryState(query, true);
        queryState.getQuery().append(SELECT_CLAUSE);

        buildSelectClause(query, queryState);
        QueryModel.Junction criteria = query.getCriteria();

        Map<String, String> parameters = null;
        if (!criteria.isEmpty()) {
            parameters = buildWhereClause(criteria, queryState);
        }

        appendOrder(query, queryState);
        return QueryResult.of(queryState.getQuery().toString(), parameters);
    }

    /**
     * Get the table name for the given entity.
     * @param entity The entity
     * @return The table name
     */
    protected abstract String getTableName(PersistentEntity entity);

    /**
     * Get the column name for the given property.
     * @param persistentProperty The property
     * @return The column name
     */
    protected abstract String getColumnName(PersistentProperty persistentProperty);

    /**
     * Obtain the string that selects all columns from the entity.
     * @param entity The entity
     * @param alias The alias to use
     * @return The columns
     */
    protected abstract String selectAllColumns(PersistentEntity entity, String alias);

    /**
     * Begins the query state.
     * @param query The query
     * @param allowJoins Whether joins are allowed
     * @return The query state object
     */
    private QueryState newQueryState(@NonNull QueryModel query, boolean allowJoins) {
        return new QueryState(query, allowJoins);
    }

    private void buildSelectClause(QueryModel query, QueryState queryState) {
        String logicalName = queryState.getLogicalName();
        PersistentEntity entity = queryState.getEntity();
        StringBuilder queryString = queryState.getQuery();
        buildSelect(
                queryState,
                queryString,
                query.getProjections(),
                logicalName,
                entity
        );

        queryString.append(FROM_CLAUSE)
                .append(getTableName(entity))
                .append(AS_CLAUSE)
                .append(logicalName);
    }

    private void buildSelect(QueryState queryState, StringBuilder queryString, List<QueryModel.Projection> projectionList, String logicalName, PersistentEntity entity) {
        if (projectionList.isEmpty()) {
            queryString.append(selectAllColumns(queryState.getEntity(), queryState.getLogicalName()));
        } else {
            for (Iterator i = projectionList.iterator(); i.hasNext();) {
                QueryModel.Projection projection = (QueryModel.Projection) i.next();
                if (projection instanceof QueryModel.CountProjection) {
                    appendProjectionRowCount(queryString, logicalName);
                } else if (projection instanceof QueryModel.DistinctProjection) {
                    queryString.append("DISTINCT(")
                            .append(logicalName)
                            .append(CLOSE_BRACKET);
                } else if (projection instanceof QueryModel.IdProjection) {
                    PersistentProperty identity = entity.getIdentity();
                    if (identity == null) {
                        throw new IllegalArgumentException("Cannot query on ID with entity that has no ID");
                    }
                    queryString.append(logicalName)
                            .append(DOT)
                            .append(identity.getName());
                } else if (projection instanceof QueryModel.PropertyProjection) {
                    QueryModel.PropertyProjection pp = (QueryModel.PropertyProjection) projection;
                    String alias = pp.getAlias().orElse(null);
                    if (projection instanceof QueryModel.AvgProjection) {
                        appendProjection(queryState.getEntity(), "AVG", pp, logicalName, queryString);
                    } else if (projection instanceof QueryModel.DistinctPropertyProjection) {
                        appendProjection(queryState.getEntity(), "DISTINCT", pp, logicalName, queryString);
                    } else if (projection instanceof QueryModel.SumProjection) {
                        appendProjection(queryState.getEntity(), "SUM", pp, logicalName, queryString);
                    } else if (projection instanceof QueryModel.MinProjection) {
                        appendProjection(queryState.getEntity(), "MIN", pp, logicalName, queryString);
                    } else if (projection instanceof QueryModel.MaxProjection) {
                        appendProjection(queryState.getEntity(), "MAX", pp, logicalName, queryString);
                    } else if (projection instanceof QueryModel.CountDistinctProjection) {
                        appendProjection(queryState.getEntity(), "COUNT(DISTINCT", pp, logicalName, queryString);
                        queryString.append(CLOSE_BRACKET);
                    } else {
                        PersistentProperty persistentProperty = entity.getPropertyByPath(pp.getPropertyName())
                                .orElseThrow(() -> new IllegalArgumentException("Cannot project on non-existent property: " + pp.getPropertyName()));
                        queryString.append(logicalName)
                                .append(DOT)
                                .append(getColumnName(persistentProperty));
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

    private void appendProjection(
            PersistentEntity entity,
            String functionName,
            QueryModel.PropertyProjection propertyProjection,
            String logicalName,
            StringBuilder queryString) {
        PersistentProperty persistentProperty = entity.getPropertyByPath(propertyProjection.getPropertyName())
                .orElseThrow(() -> new IllegalArgumentException("Cannot project on non-existent property: " + propertyProjection.getPropertyName()));
        queryString.append(functionName)
                .append(OPEN_BRACKET)
                .append(logicalName)
                .append(DOT)
                .append(getColumnName(persistentProperty))
                .append(CLOSE_BRACKET);
    }



    /**
     * Appends a row count projection to the query string.
     * @param queryString The query string
     * @param logicalName The alias to the table name
     */
    protected abstract void appendProjectionRowCount(StringBuilder queryString, String logicalName);

    private void handleAssociationCriteria(
            QueryState queryState,
            Association association,
            QueryModel.Junction associationCriteria,
            List<QueryModel.Criterion> associationCriteriaList) {
        if (association == null) {
            return;
        }
        String currentName = queryState.getLogicalName();
        PersistentEntity currentEntity = queryState.getEntity();
        final PersistentEntity associatedEntity = association.getAssociatedEntity();
        if (associatedEntity != null) {

            if (association.getKind() == Relation.Kind.ONE_TO_ONE) {
                final String associationName = association.getName();
                try {
                    queryState.setEntity(associatedEntity);
                    queryState.setLogicalName(currentName + DOT + associationName);
                    buildWhereClauseForCriterion(
                            queryState,
                            associationCriteria,
                            associationCriteriaList
                    );
                } finally {
                    queryState.setLogicalName(currentName);
                    queryState.setEntity(currentEntity);
                }
            }

            final String associationName = association.getName();
            String associationPath = queryState.getLogicalName() + DOT + associationName;
            if (!queryState.getAppliedJoinPaths().contains(associationPath)) {
                queryState.getAppliedJoinPaths().add(associationPath);
                Join.Type jt = queryState.getQueryObject().getJoinType(association).orElse(Join.Type.DEFAULT);
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


                queryState.getQuery().append(joinType)
                        .append(queryState.getLogicalName())
                        .append(DOT)
                        .append(associationName)
                        .append(SPACE)
                        .append(associationName);
            }

            try {
                queryState.setEntity(associatedEntity);
                queryState.setLogicalName(associationName);
                buildWhereClauseForCriterion(
                        queryState,
                        associationCriteria,
                        associationCriteriaList
                );
            } finally {
                queryState.setLogicalName(currentName);
                queryState.setEntity(currentEntity);
            }
        }

    }

    private Map<String, String> buildWhereClause(
            QueryModel.Junction criteria,
            QueryState queryState) {
        if (!criteria.isEmpty()) {

            final List<QueryModel.Criterion> criterionList = criteria.getCriteria();
            StringBuilder whereClause = queryState.getWhereClause();
            whereClause.append(WHERE_CLAUSE);
            if (criteria instanceof QueryModel.Negation) {
                whereClause.append(NOT_CLAUSE);
            }
            whereClause.append(OPEN_BRACKET);
            buildWhereClauseForCriterion(queryState, criteria, criterionList);
            String whereStr = whereClause.toString();
            if (!whereStr.equals(WHERE_CLAUSE + OPEN_BRACKET)) {
                queryState.getQuery().append(whereStr);
                queryState.getQuery().append(CLOSE_BRACKET);
            }
        }
        return queryState.getParameters();
    }

    private void appendOrder(QueryModel query, QueryState queryState) {
        List<Sort.Order> orders = query.getSort().getOrderBy();
        if (!orders.isEmpty()) {

            StringBuilder buff = queryState.getQuery();
            buff.append(ORDER_BY_CLAUSE);
            Iterator<Sort.Order> i = orders.iterator();
            while (i.hasNext()) {
                Sort.Order order = i.next();
                buff.append(queryState.getLogicalName())
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

    private void buildWhereClauseForCriterion(
            final QueryState queryState,
            QueryModel.Junction criteria,
            final List<QueryModel.Criterion> criterionList) {
        boolean isFirst = true;
        for (QueryModel.Criterion criterion : criterionList) {
            final String operator = criteria instanceof QueryModel.Conjunction ? LOGICAL_AND : LOGICAL_OR;
            QueryHandler qh = queryHandlers.get(criterion.getClass());
            boolean isAssociationCriteria = criterion instanceof AssociationQuery;
            if (qh != null) {
                if (!isFirst) {
                    if (isAssociationCriteria) {
                        if (!((AssociationQuery) criterion).getCriteria().getCriteria().isEmpty()) {
                            queryState.getWhereClause().append(operator);
                        }
                    } else {
                        queryState.getWhereClause().append(operator);
                    }

                }

                qh.handle(queryState, criterion);
            } else {
                if (isAssociationCriteria) {

                    if (!queryState.isAllowJoins()) {
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

    private void appendCriteriaForOperator(QueryState queryState,
                                           PersistentProperty property,
                                           final String path,
                                           Object value,
                                           String operator) {
        String parameterName = newParameter(queryState.getParameterPosition());
        queryState.getWhereClause().append(queryState.getLogicalName())
                .append(DOT)
                // TODO: properly handle paths
                .append(path.indexOf('.') == -1 ? getColumnName(property) : path)
                .append(operator)
                .append(':')
                .append(parameterName);
        if (value instanceof QueryParameter) {
            queryState.getParameters().put(parameterName, ((QueryParameter) value).getName());
        }
    }

    private String newParameter(AtomicInteger position) {
        return "p" + position.incrementAndGet();
    }

    private void appendCaseInsensitiveCriterion(QueryState queryState, QueryModel.PropertyCriterion criterion, PersistentProperty prop, String operator) {
        String parameterName = newParameter(queryState.getParameterPosition());
        queryState.getWhereClause().append("lower(")
                .append(queryState.getLogicalName())
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
            queryState.getParameters().put(parameterName, ((QueryParameter) value).getName());
        }
    }

    /**
     * For handling subqueries.
     *
     * @param queryState The query state
     * @param subqueryCriterion The subquery criterion
     * @param comparisonExpression The comparison expression
     */
    protected void handleSubQuery(QueryState queryState, QueryModel.SubqueryCriterion subqueryCriterion, String comparisonExpression) {
        final String name = subqueryCriterion.getProperty();
        validateProperty(queryState.getEntity(), name, QueryModel.In.class);
        QueryModel subquery = subqueryCriterion.getValue();
        queryState.getWhereClause().append(queryState.getLogicalName())
                .append(DOT)
                .append(name)
                .append(comparisonExpression);
        buildSubQuery(queryState, subquery);
        queryState.getWhereClause().append(CLOSE_BRACKET);
    }

    private void buildSubQuery(QueryState queryState, QueryModel subquery) {
        PersistentEntity associatedEntity = subquery.getPersistentEntity();
        String associatedEntityName = associatedEntity.getName();
        String associatedEntityLogicalName = associatedEntity.getDecapitalizedName() + queryState.getParameterPosition().incrementAndGet();
        queryState.getWhereClause().append("SELECT ");
        buildSelect(queryState, queryState.getWhereClause(), subquery.getProjections(), associatedEntityLogicalName, associatedEntity);
        queryState.getWhereClause().append(" FROM ")
                .append(associatedEntityName)
                .append(' ')
                .append(associatedEntityLogicalName)
                .append(" WHERE ");
        List<QueryModel.Criterion> criteria = subquery.getCriteria().getCriteria();
        for (QueryModel.Criterion subCriteria : criteria) {
            QueryHandler queryHandler = queryHandlers.get(subCriteria.getClass());
            if (queryHandler != null) {
                queryHandler.handle(
                        queryState,
                        subCriteria
                );
            }
        }
    }

    private void buildUpdateStatement(
            QueryState queryState,
            List<String> propertiesToUpdate) {
        StringBuilder queryString = queryState.getQuery();
        Map<String, String> parameters = queryState.getParameters();
        queryString.append(SPACE).append("SET");

        // keys need to be sorted before query is built

        Iterator<String> iterator = propertiesToUpdate.iterator();
        while (iterator.hasNext()) {
            String propertyName = iterator.next();
            PersistentProperty prop = queryState.getEntity().getPropertyByName(propertyName);
            if (prop == null) {
                continue;
            }

            queryString.append(SPACE).append(queryState.getLogicalName()).append(DOT).append(propertyName).append('=');
            String param = newParameter(queryState.getParameterPosition());
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

    @NonNull
    @Override
    public QueryResult buildUpdate(@NonNull QueryModel query, List<String> propertiesToUpdate) {
        if (propertiesToUpdate.isEmpty()) {
            throw new IllegalArgumentException("No properties specified to update");
        }
        PersistentEntity entity = query.getPersistentEntity();
        QueryState queryState = newQueryState(query, false);
        StringBuilder queryString = queryState.getQuery();
        queryString.append(UPDATE_CLAUSE)
                .append(getTableName(entity))
                .append(SPACE)
                .append(queryState.getLogicalName());
        buildUpdateStatement(queryState, propertiesToUpdate);
        buildWhereClause(query.getCriteria(), queryState);
        return QueryResult.of(queryString.toString(), queryState.getParameters());
    }

    @NonNull
    @Override
    public QueryResult buildDelete(@NonNull QueryModel query) {
        PersistentEntity entity = query.getPersistentEntity();
        QueryState queryState = newQueryState(query, false);
        StringBuilder queryString = queryState.getQuery();
        queryString.append(DELETE_CLAUSE)
                .append(getTableName(entity)).append(SPACE)
                .append(queryState.getLogicalName());
        buildWhereClause(query.getCriteria(), queryState);
        return QueryResult.of(queryString.toString(), queryState.getParameters());
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

    /**
     * A query handler.
     */
    protected interface QueryHandler {
        /**
         * Handles a criterion.
         * @param queryState The query state
         * @param criterion The criterion
         */
        void handle(
                QueryState queryState,
                QueryModel.Criterion criterion
        );
    }

    /**
     * The state of the query.
     */
    protected final class QueryState {
        private final Set<String> appliedJoinPaths = new HashSet<>();
        private final AtomicInteger position = new AtomicInteger(0);
        private final Map<String, String> parameters  = new LinkedHashMap<>();
        private final StringBuilder query = new StringBuilder();
        private final StringBuilder whereClause = new StringBuilder();
        private final boolean allowJoins;
        private final QueryModel queryObject;
        private String logicalName;
        private PersistentEntity entity;

        private QueryState(QueryModel query, boolean allowJoins) {
            this.allowJoins = allowJoins;
            this.queryObject = query;
            this.entity = query.getPersistentEntity();
            this.logicalName = entity.getDecapitalizedName();
        }

        /**
         * @return The current logic name
         */
        public String getLogicalName() {
            return logicalName;
        }

        /**
         * Sets the current logic name.
         *
         * @param logicalName The logical name
         */
        public void setLogicalName(@NonNull String logicalName) {
            this.logicalName = logicalName;
        }

        /**
         * @return The entity
         */
        public PersistentEntity getEntity() {
            return entity;
        }

        /**
         * Sets the current entity.
         * @param entity The entity to set
         */
        public void setEntity(@NonNull PersistentEntity entity) {
            this.entity = entity;
        }

        /**
         * @return The applied join paths
         */
        public Set<String> getAppliedJoinPaths() {
            return appliedJoinPaths;
        }

        /**
         * The current parameter position.
         * @return The parameter position
         */
        public AtomicInteger getParameterPosition() {
            return position;
        }

        /**
         * @return The constructed parameters
         */
        public Map<String, String> getParameters() {
            return parameters;
        }

        /**
         * @return The query string
         */
        public StringBuilder getQuery() {
            return query;
        }

        /**
         * @return The where string
         */
        public StringBuilder getWhereClause() {
            return whereClause;
        }

        /**
         * @return Does the query allow joins
         */
        public boolean isAllowJoins() {
            return allowJoins;
        }

        /**
         * @return The query model object
         */
        public QueryModel getQueryObject() {
            return queryObject;
        }
    }
}
