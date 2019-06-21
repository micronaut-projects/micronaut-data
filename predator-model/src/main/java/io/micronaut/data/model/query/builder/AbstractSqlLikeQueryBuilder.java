package io.micronaut.data.model.query.builder;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
    public static final String ORDER_BY_CLAUSE = " ORDER BY ";
    protected static final String SELECT_CLAUSE = "SELECT ";
    protected static final String AS_CLAUSE = " AS ";
    protected static final String FROM_CLAUSE = " FROM ";
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
            String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState, name, QueryModel.Equals.class);
            if (eq.isIgnoreCase()) {
                appendCaseInsensitiveCriterion(
                        queryState,
                        eq,
                        prop,
                        name,
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

            PersistentProperty left = validateProperty(queryState, propertyName, QueryModel.EqualsProperty.class);
            PersistentProperty right = validateProperty(queryState, otherProperty, QueryModel.EqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getCurrentAlias(), propertyName, otherProperty, "=");
        });

        queryHandlers.put(QueryModel.NotEqualsProperty.class, (queryState, criterion) -> {
            final PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PersistentProperty left = validateProperty(queryState, propertyName, QueryModel.NotEqualsProperty.class);
            PersistentProperty right = validateProperty(queryState, otherProperty, QueryModel.NotEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getCurrentAlias(), propertyName, otherProperty, "!=");
        });

        queryHandlers.put(QueryModel.GreaterThanProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PersistentProperty left = validateProperty(queryState, propertyName, QueryModel.GreaterThanProperty.class);
            PersistentProperty right = validateProperty(queryState, otherProperty, QueryModel.GreaterThanProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getCurrentAlias(), propertyName, otherProperty, ">");
        });

        queryHandlers.put(QueryModel.GreaterThanEqualsProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PersistentProperty left = validateProperty(queryState, propertyName, QueryModel.GreaterThanEqualsProperty.class);
            PersistentProperty right = validateProperty(queryState, otherProperty, QueryModel.GreaterThanEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getCurrentAlias(), propertyName, otherProperty, ">=");
        });

        queryHandlers.put(QueryModel.LessThanProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PersistentProperty left = validateProperty(queryState, propertyName, QueryModel.LessThanProperty.class);
            PersistentProperty right = validateProperty(queryState, otherProperty, QueryModel.LessThanProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getCurrentAlias(), propertyName, otherProperty, "<");
        });

        queryHandlers.put(QueryModel.LessThanEqualsProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PersistentProperty left = validateProperty(queryState, propertyName, QueryModel.LessThanEqualsProperty.class);
            PersistentProperty right = validateProperty(queryState, otherProperty, QueryModel.LessThanEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getCurrentAlias(), propertyName, otherProperty, "<=");
        });

        queryHandlers.put(QueryModel.IsNull.class, (queryState, criterion) -> {
            QueryModel.IsNull isNull = (QueryModel.IsNull) criterion;
            applyPropertyExpression(queryState, isNull, QueryModel.IsNull.class, " IS NULL ");
        });

        queryHandlers.put(QueryModel.IsTrue.class, (queryState, criterion) -> {
            QueryModel.IsTrue isTrue = (QueryModel.IsTrue) criterion;
            applyPropertyExpression(queryState, isTrue, QueryModel.IsTrue.class, " = TRUE ");
        });

        queryHandlers.put(QueryModel.IsFalse.class, (queryState, criterion) -> {
            QueryModel.IsFalse isFalse = (QueryModel.IsFalse) criterion;
            applyPropertyExpression(queryState, isFalse, QueryModel.IsFalse.class, " = FALSE ");
        });

        queryHandlers.put(QueryModel.IsNotNull.class, (queryState, criterion) -> {
            QueryModel.IsNotNull isNotNull = (QueryModel.IsNotNull) criterion;
            applyPropertyExpression(queryState, isNotNull, QueryModel.IsNotNull.class, " IS NOT NULL ");
        });

        queryHandlers.put(QueryModel.IsEmpty.class, (queryState, criterion) -> {
            QueryModel.IsEmpty isEmpty = (QueryModel.IsEmpty) criterion;
            final String name = isEmpty.getProperty();
            appendEmptyExpression(queryState, " IS NULL OR ", " = '' ", " IS EMPTY ", name);
        });

        queryHandlers.put(QueryModel.IsNotEmpty.class, (queryState, criterion) -> {
            QueryModel.IsNotEmpty isNotEmpty = (QueryModel.IsNotEmpty) criterion;
            final String name = isNotEmpty.getProperty();
            appendEmptyExpression(queryState, " IS NOT NULL AND ", " <> '' ", " IS NOT EMPTY ", name);
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
            PersistentProperty prop = validateProperty(queryState, name, QueryModel.NotEquals.class);
            if (eq.isIgnoreCase()) {
                appendCaseInsensitiveCriterion(
                        queryState,
                        eq,
                        prop,
                        name,
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
            PersistentProperty prop = validateProperty(queryState, name, QueryModel.GreaterThan.class);
            appendCriteriaForOperator(
                    queryState, prop, name, eq.getValue(), " > "
            );
        });

        queryHandlers.put(QueryModel.LessThanEquals.class, (queryState, criterion) -> {
            QueryModel.LessThanEquals eq = (QueryModel.LessThanEquals) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState, name, QueryModel.LessThanEquals.class);
            appendCriteriaForOperator(
                    queryState, prop, name, eq.getValue(), " <= "
            );
        });

        queryHandlers.put(QueryModel.GreaterThanEquals.class, (queryState, criterion) -> {
            QueryModel.GreaterThanEquals eq = (QueryModel.GreaterThanEquals) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState, name, QueryModel.GreaterThanEquals.class);
            appendCriteriaForOperator(
                    queryState, prop, name, eq.getValue(), " >= "
            );
        });

        queryHandlers.put(QueryModel.Between.class, (queryState, criterion) -> {
            QueryModel.Between between = (QueryModel.Between) criterion;
            final String name = between.getProperty();
            PersistentProperty prop = validateProperty(queryState, name, QueryModel.Between.class);
            final String qualifiedName = queryState.getCurrentAlias() + DOT + (isApplyManualJoins() ? getColumnName(prop) : name);
            Placeholder fromParam = queryState.newParameter();
            Placeholder toParam = queryState.newParameter();
            queryState.getWhereClause().append(OPEN_BRACKET)
                    .append(qualifiedName)
                    .append(" >= ")
                    .append(fromParam.name);
            queryState.getWhereClause().append(" AND ")
                    .append(qualifiedName)
                    .append(" <= ")
                    .append(toParam.name)
                    .append(CLOSE_BRACKET);

            queryState.getParameters().put(fromParam.key, between.getFrom().getName());
            queryState.getParameters().put(toParam.key, between.getTo().getName());
        });

        queryHandlers.put(QueryModel.LessThan.class, (queryState, criterion) -> {
            QueryModel.LessThan eq = (QueryModel.LessThan) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState, name, QueryModel.LessThan.class);
            appendCriteriaForOperator(
                    queryState, prop, name, eq.getValue(), " < "
            );
        });

        queryHandlers.put(QueryModel.Like.class, (queryState, criterion) -> {
            QueryModel.Like eq = (QueryModel.Like) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState, name, QueryModel.Like.class);
            appendCriteriaForOperator(
                    queryState, prop, name, eq.getValue(), " like "
            );
        });

        queryHandlers.put(QueryModel.ILike.class, (queryState, criterion) -> {
            QueryModel.ILike eq = (QueryModel.ILike) criterion;
            final String name = eq.getProperty();
            PersistentProperty prop = validateProperty(queryState, name, QueryModel.ILike.class);
            String operator = "like";
            appendCaseInsensitiveCriterion(queryState, eq, prop, name, operator);
        });

        queryHandlers.put(QueryModel.StartsWith.class, (queryState, criterion) -> {
            QueryModel.StartsWith eq = (QueryModel.StartsWith) criterion;
            appendLikeComparison(queryState, eq, " LIKE CONCAT(", ",'%')");
        });

        queryHandlers.put(QueryModel.Contains.class, (queryState, criterion) -> {
            QueryModel.Contains eq = (QueryModel.Contains) criterion;
            appendLikeComparison(queryState, eq, " LIKE CONCAT('%',", ",'%')");
        });

        queryHandlers.put(QueryModel.EndsWith.class, (queryState, criterion) -> {
            QueryModel.EndsWith eq = (QueryModel.EndsWith) criterion;
            appendLikeComparison(queryState, eq, " LIKE CONCAT('%',", ")");
        });

        queryHandlers.put(QueryModel.In.class, (queryState, criterion) -> {
            QueryModel.In inQuery = (QueryModel.In) criterion;
            final String name = inQuery.getProperty();
            PersistentProperty prop = validateProperty(queryState, name, QueryModel.In.class);
            Object value = inQuery.getValue();
            if (value instanceof QueryParameter) {
                QueryParameter queryParameter = (QueryParameter) value;
                String currentAlias = queryState.getCurrentAlias();
                final String qualifiedName;
                if (currentAlias != null) {
                    qualifiedName = currentAlias + DOT + (isApplyManualJoins() ? getColumnName(prop) : name);
                } else {
                    qualifiedName = (isApplyManualJoins() ? getColumnName(prop) : name);
                }
                Placeholder placeholder = queryState.newParameter();
                queryState.getParameters().put(placeholder.key, queryParameter.getName());
                StringBuilder whereClause = queryState.getWhereClause();
                whereClause
                        .append(qualifiedName);
                encodeInExpression(whereClause, placeholder);
            }

        });

        queryHandlers.put(QueryModel.NotIn.class, (queryState, criterion) -> {
            String comparisonExpression = " NOT IN (";
            handleSubQuery(queryState, (QueryModel.SubqueryCriterion) criterion, comparisonExpression);
        });
    }

    private void appendEmptyExpression(QueryState queryState, String charSequencePrefix, String charSequenceSuffix, String listSuffix, String name) {
        PersistentProperty property = validateProperty(queryState, name, QueryModel.IsEmpty.class);
        String aliasRef = isApplyManualJoins() ? getColumnName(property) : name;
        String currentAlias = queryState.getCurrentAlias();
        String path = currentAlias == null ? "" :  currentAlias + DOT;
        if (property.isAssignable(CharSequence.class)) {

            queryState.getWhereClause()
                    .append(path)
                    .append(aliasRef)
                    .append(charSequencePrefix)
                    .append(path)
                    .append(aliasRef)
                    .append(charSequenceSuffix);

        } else {

            queryState.getWhereClause().append(path)
                    .append(aliasRef)
                    .append(listSuffix);
        }
    }

    private void appendLikeComparison(QueryState queryState, QueryModel.PropertyCriterion eq, String prefix, String suffix) {
        final String name = eq.getProperty();
        PersistentProperty prop = validateProperty(queryState, name, QueryModel.ILike.class);
        String currentAlias = queryState.getCurrentAlias();
        final String qualifiedName;
        if (currentAlias != null) {
            qualifiedName = currentAlias + DOT + (isApplyManualJoins() ? getColumnName(prop) : name);
        } else {
            qualifiedName = (isApplyManualJoins() ? getColumnName(prop) : name);
        }
        Placeholder parameterName = queryState.newParameter();
        queryState.getWhereClause()
                .append(qualifiedName)
                .append(prefix)
                .append(parameterName.name)
                .append(suffix);
        Object value = eq.getValue();
        if (value instanceof QueryParameter) {
            queryState.getParameters().put(parameterName.key, ((QueryParameter) value).getName());
        }
    }

    private void applyPropertyExpression(QueryState queryState, QueryModel.PropertyNameCriterion propertyNameCriterion, Class<?> criterionType, String expression) {
        final String name = propertyNameCriterion.getProperty();
        PersistentProperty prop = validateProperty(queryState, name, criterionType);
        String alias = queryState.getCurrentAlias();
        StringBuilder whereClause = queryState.getWhereClause();
        if (alias != null) {
            whereClause.append(alias)
                    .append(DOT);
        }

        whereClause.append(isApplyManualJoins() ? getColumnName(prop) : name)
                .append(expression);
    }

    /**
     * Placeholders for IN queries in SQL require special treatment. This is handled at runtime by some wrapper implementations like JPAQL.
     * But for raw queries the placeholder needs to be expanded to factor in the size of the list or array.
     *
     * @param whereClause The where clause
     * @param placeholder The placeholder
     */
    protected void encodeInExpression(StringBuilder whereClause, Placeholder placeholder) {
        whereClause
                .append(" IN (")
                .append(placeholder.name)
                .append(CLOSE_BRACKET);
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
     *
     * @param entity The entity
     * @return The table name
     */
    protected abstract String getTableName(PersistentEntity entity);

    /**
     * Get an alias name for the given entity.
     *
     * @param entity The entity
     * @return The alias name
     */
    protected String getAliasName(PersistentEntity entity) {
        return getTableName(entity) + "_";
    }

    /**
     * Get the alias name.
     *
     * @param association The association
     * @return The alias
     */
    protected String getAliasName(Association association) {
        return getAliasName(association.getOwner()) + getColumnName(association) + "_";
    }

    /**
     * Build a join expression for the given alias, association, join type and builder.
     *
     * @param alias         The alias
     * @param association   The association
     * @param joinType      The join type
     * @param stringBuilder The target builder
     * @return The join string
     */
    protected abstract String buildJoin(String alias, Association association, String joinType, StringBuilder stringBuilder);

    /**
     * Get the column name for the given property.
     *
     * @param persistentProperty The property
     * @return The column name
     */
    protected abstract String getColumnName(PersistentProperty persistentProperty);

    /**
     * Obtain the string that selects all columns from the entity.
     *
     * @param entity The entity
     * @param alias  The alias to use
     * @return The columns
     */
    protected abstract String selectAllColumns(PersistentEntity entity, String alias);

    /**
     * Begins the query state.
     *
     * @param query      The query
     * @param allowJoins Whether joins are allowed
     * @return The query state object
     */
    private QueryState newQueryState(@NonNull QueryModel query, boolean allowJoins) {
        return new QueryState(query, allowJoins);
    }

    private void buildSelectClause(QueryModel query, QueryState queryState) {
        String logicalName = queryState.getCurrentAlias();
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
            queryString.append(selectAllColumns(queryState.getEntity(), queryState.getCurrentAlias()));
        } else {
            for (Iterator i = projectionList.iterator(); i.hasNext(); ) {
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
     *
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
        String currentName = queryState.getCurrentAlias();
        PersistentEntity currentEntity = queryState.getEntity();
        final PersistentEntity associatedEntity = association.getAssociatedEntity();
        if (associatedEntity != null) {

            if (association.getKind() == Relation.Kind.ONE_TO_ONE) {
                final String associationName = association.getName();
                try {
                    queryState.setEntity(associatedEntity);
                    queryState.setCurrentAlias(currentName + DOT + associationName);
                    buildWhereClauseForCriterion(
                            queryState,
                            associationCriteria,
                            associationCriteriaList
                    );
                } finally {
                    queryState.setCurrentAlias(currentName);
                    queryState.setEntity(currentEntity);
                }
            }

            String alias = queryState.applyJoin(association);

            try {
                queryState.setEntity(associatedEntity);
                queryState.setCurrentAlias(alias);
                buildWhereClauseForCriterion(
                        queryState,
                        associationCriteria,
                        associationCriteriaList
                );
            } finally {
                queryState.setCurrentAlias(currentName);
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
                String currentAlias = queryState.getCurrentAlias();
                if (currentAlias != null) {
                    buff.append(currentAlias)
                            .append(DOT);
                }

                buff.append(order.getProperty())
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
                String currentAlias = queryState.getCurrentAlias();
                try {
                    qh.handle(queryState, criterion);
                } finally {
                    queryState.setCurrentAlias(currentAlias);
                }
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
        Placeholder placeholder = queryState.newParameter();
        String alias = queryState.getCurrentAlias();
        StringBuilder whereClause = queryState.getWhereClause();
        if (alias != null) {
            whereClause.append(alias)
                    .append(DOT);
        }

        whereClause.append(isApplyManualJoins() ? getColumnName(property) : path)
                .append(operator)
                .append(placeholder.name);
        if (value instanceof QueryParameter) {
            queryState.getParameters().put(placeholder.key, ((QueryParameter) value).getName());
        }
    }

    private void appendCaseInsensitiveCriterion(QueryState queryState, QueryModel.PropertyCriterion criterion, PersistentProperty prop, String path, String operator) {
        Placeholder placeholder = queryState.newParameter();
        StringBuilder whereClause = queryState.getWhereClause();
        String currentAlias = queryState.getCurrentAlias();
        whereClause.append("lower(");

        if (currentAlias != null) {
            whereClause.append(currentAlias)
                    .append(DOT);
        }

        whereClause.append(isApplyManualJoins() ? getColumnName(prop) : path)
                .append(") ")
                .append(operator)
                .append(" lower(")
                .append(placeholder.name)
                .append(")");
        Object value = criterion.getValue();
        if (value instanceof QueryParameter) {
            queryState.getParameters().put(placeholder.key, ((QueryParameter) value).getName());
        }
    }

    /**
     * For handling subqueries.
     *
     * @param queryState           The query state
     * @param subqueryCriterion    The subquery criterion
     * @param comparisonExpression The comparison expression
     */
    protected void handleSubQuery(QueryState queryState, QueryModel.SubqueryCriterion subqueryCriterion, String comparisonExpression) {
        final String name = subqueryCriterion.getProperty();
        validateProperty(queryState, name, QueryModel.In.class);
        QueryModel subquery = subqueryCriterion.getValue();
        String currentAlias = queryState.getCurrentAlias();

        StringBuilder whereClause = queryState.getWhereClause();
        if (currentAlias != null) {
            whereClause.append(currentAlias)
                    .append(DOT);
        }

        whereClause.append(name)
                   .append(comparisonExpression);
        buildSubQuery(queryState, subquery);
        whereClause.append(CLOSE_BRACKET);
    }

    private void buildSubQuery(QueryState queryState, QueryModel subquery) {
//        TODO: Support subqueries
//        PersistentEntity associatedEntity = subquery.getPersistentEntity();
//        String associatedEntityName = associatedEntity.getName();
//        String associatedEntityLogicalName = associatedEntity.getDecapitalizedName() + queryState.getParameterPosition().incrementAndGet();
//        queryState.getWhereClause().append("SELECT ");
//        buildSelect(queryState, queryState.getWhereClause(), subquery.getProjections(), associatedEntityLogicalName, associatedEntity);
//        queryState.getWhereClause().append(" FROM ")
//                .append(associatedEntityName)
//                .append(' ')
//                .append(associatedEntityLogicalName)
//                .append(" WHERE ");
//        List<QueryModel.Criterion> criteria = subquery.getCriteria().getCriteria();
//        for (QueryModel.Criterion subCriteria : criteria) {
//            QueryHandler queryHandler = queryHandlers.get(subCriteria.getClass());
//            if (queryHandler != null) {
//                queryHandler.handle(
//                        queryState,
//                        subCriteria
//                );
//            }
//        }
    }

    private void buildUpdateStatement(
            QueryState queryState,
            List<String> propertiesToUpdate) {
        StringBuilder queryString = queryState.getQuery();
        Map<String, String> parameters = queryState.getParameters();
        queryString.append(SPACE).append("SET").append(SPACE);

        // keys need to be sorted before query is built

        Iterator<String> iterator = propertiesToUpdate.iterator();
        while (iterator.hasNext()) {
            String propertyName = iterator.next();
            PersistentProperty prop = queryState.getEntity().getPropertyByName(propertyName);
            if (prop == null) {
                continue;
            }

            if (prop instanceof Association) {
                if (((Association) prop).isForeignKey()) {
                    throw new IllegalArgumentException("Foreign key associations cannot be updated as part of a batch update statement");
                }
            }

            String currentAlias = queryState.getCurrentAlias();
            if (currentAlias != null) {
                queryString.append(currentAlias).append(DOT);
            }
            queryString.append(getColumnName(prop)).append('=');
            Placeholder param = queryState.newParameter();
            queryString.append(param.name);
            parameters.put(param.key, prop.getName());
            if (iterator.hasNext()) {
                queryString.append(COMMA);
            }
        }
    }

    private static void appendPropertyComparison(StringBuilder q, String alias, String propertyName, String otherProperty, String operator) {
        if (alias != null) {
            q.append(alias).append(DOT);
        }

        q.append(propertyName)
                .append(operator);

        if (alias != null) {
            q.append(alias)
                    .append(DOT);
        }

        q.append(otherProperty);
    }

    private PersistentProperty validateProperty(QueryState queryState, String name, Class criterionType) {
        PersistentEntity entity = queryState.getEntity();
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
                        criterionType.getSimpleName() + "] criterion on non-existent property path: " + name);
            }
        }

        if (isApplyManualJoins() && name.contains(".")) {
            StringTokenizer tokenizer = new StringTokenizer(name, ".");
            String first = tokenizer.nextToken();
            PersistentProperty p = queryState.getEntity().getPropertyByName(first);
            if (p instanceof Association) {
                if (queryState.isAllowJoins()) {
                    String alias = queryState.applyJoin((Association) p);
                    queryState.setCurrentAlias(alias);
                } else {
                    throw new IllegalArgumentException("Joins are not allowed for batch update queries");
                }
            }
        }
        return prop;
    }

    /**
     * Whether manual query joins need to be built.
     *
     * @return True if manual query joins are required.
     */
    protected abstract boolean isApplyManualJoins();

    @NonNull
    @Override
    public QueryResult buildUpdate(@NonNull QueryModel query, List<String> propertiesToUpdate) {
        if (propertiesToUpdate.isEmpty()) {
            throw new IllegalArgumentException("No properties specified to update");
        }
        PersistentEntity entity = query.getPersistentEntity();
        QueryState queryState = newQueryState(query, false);
        StringBuilder queryString = queryState.getQuery();
        if (!isAliasForBatch()) {
            queryState.setCurrentAlias(null);
        }
        String currentAlias = queryState.getCurrentAlias();
        queryString.append(UPDATE_CLAUSE)
                .append(getTableName(entity));
        if (currentAlias != null) {
            queryString.append(SPACE)
                        .append(currentAlias);
        }
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
        String currentAlias = queryState.getCurrentAlias();
        if (!isAliasForBatch()) {
            currentAlias = null;
            queryState.setCurrentAlias(null);
        }
        StringBuilder buffer = appendDeleteClause(queryString);
        buffer.append(getTableName(entity)).append(SPACE);
        if (currentAlias != null) {
            buffer.append(AS_CLAUSE)
                    .append(currentAlias);
        }
        buildWhereClause(query.getCriteria(), queryState);
        return QueryResult.of(queryString.toString(), queryState.getParameters());
    }

    /**
     * Should aliases be used in batch statements.
     * @return True if they should
     */
    protected abstract boolean isAliasForBatch();


    /**
     * Append the delete clause.
     *
     * @param queryString The query string
     * @return The delete clause
     */
    @NonNull
    protected StringBuilder appendDeleteClause(StringBuilder queryString) {
        return queryString.append(DELETE_CLAUSE).append(FROM_CLAUSE);
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
        Map<PersistentProperty, Sort.Order.Direction> orderMap = new LinkedHashMap<>();
        for (Sort.Order order : orders) {
            String property = order.getProperty();
            Optional<PersistentProperty> prop = entity.getPropertyByPath(property);
            if (!prop.isPresent()) {
                throw new IllegalArgumentException("Cannot sort on non-existent property path: " + property);
            } else {
                orderMap.put(prop.get(), order.getDirection());
            }
        }

        StringBuilder buff = new StringBuilder(ORDER_BY_CLAUSE);
        Iterator<Map.Entry<PersistentProperty, Sort.Order.Direction>> i = orderMap.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<PersistentProperty, Sort.Order.Direction> entry = i.next();
            PersistentProperty key = entry.getKey();

            String aliasName;
            if (key instanceof Association) {
                aliasName = getAliasName((Association) key);
            } else {
                aliasName = getAliasName(entity);
            }
            buff.append(aliasName)
                    .append(DOT)
                    .append(getColumnName(key))
                    .append(SPACE)
                    .append(entry.getValue());
            if (i.hasNext()) {
                buff.append(",");
            }
        }

        return QueryResult.of(buff.toString(), Collections.emptyMap());
    }

    /**
     * Format the parameter at the given index.
     *
     * @param index The parameter
     * @return The index
     */
    protected abstract Placeholder formatParameter(int index);

    /**
     * Resolves the join type.
     *
     * @param jt The join type
     * @return The join type.
     */
    public abstract String resolveJoinType(Join.Type jt);

    /**
     * A query handler.
     */
    protected interface QueryHandler {
        /**
         * Handles a criterion.
         *
         * @param queryState The query state
         * @param criterion  The criterion
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
        private final Map<String, String> appliedJoinPaths = new HashMap<>();
        private final AtomicInteger position = new AtomicInteger(0);
        private final Map<String, String> parameters = new LinkedHashMap<>();
        private final StringBuilder query = new StringBuilder();
        private final StringBuilder whereClause = new StringBuilder();
        private final boolean allowJoins;
        private final QueryModel queryObject;
        private String currentAlias;
        private PersistentEntity entity;

        private QueryState(QueryModel query, boolean allowJoins) {
            this.allowJoins = allowJoins;
            this.queryObject = query;
            this.entity = query.getPersistentEntity();
            this.currentAlias = getAliasName(entity);
        }

        /**
         * @return The current current alias
         */
        public @Nullable String getCurrentAlias() {
            return currentAlias;
        }

        /**
         * Sets the current logic name.
         *
         * @param currentAlias The current alias
         */
        public void setCurrentAlias(@Nullable String currentAlias) {
            this.currentAlias = currentAlias;
        }

        /**
         * @return The entity
         */
        public PersistentEntity getEntity() {
            return entity;
        }

        /**
         * Sets the current entity.
         *
         * @param entity The entity to set
         */
        public void setEntity(@NonNull PersistentEntity entity) {
            this.entity = entity;
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

        /**
         * Constructs a new parameter placeholder.
         *
         * @return The parameter
         */
        public Placeholder newParameter() {
            return formatParameter(position.incrementAndGet());
        }

        /**
         * Applies a join for the given association.
         *
         * @param association The association
         * @return The alias
         */
        public String applyJoin(Association association) {
            String alias = getCurrentAlias();
            String associationName = association.getName();
            String associationPath = alias + DOT + associationName;
            if (!appliedJoinPaths.containsKey(associationPath)) {
                StringBuilder stringBuilder = getQuery();
                Join.Type jt = getQueryObject().getJoinType(association).orElse(Join.Type.DEFAULT);
                String joinType = resolveJoinType(jt);


                String associationAlias = buildJoin(alias, association, joinType, stringBuilder);
                appliedJoinPaths.put(associationPath, associationAlias);
                return associationAlias;
            } else {
                return appliedJoinPaths.get(associationPath);
            }
        }
    }

    /**
     * Represents a placeholder in query.
     */
    protected class Placeholder {
        private final String name;
        private final String key;

        /**
         * Default constructor.
         *
         * @param name The name of the place holder
         * @param key  The key to set the value of the place holder
         */
        public Placeholder(String name, String key) {
            this.name = name;
            this.key = key;
        }

        @Override
        public String toString() {
            return name;
        }

        /**
         * @return The place holder name
         */
        public String getName() {
            return name;
        }

        /**
         * This the precomputed key to set the place holder. In SQL this would be the index.
         *
         * @return The key used to set the placeholder.
         */
        public String getKey() {
            return key;
        }
    }

}
