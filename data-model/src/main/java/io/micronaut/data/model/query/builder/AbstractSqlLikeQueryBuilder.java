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
package io.micronaut.data.model.query.builder;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.repeatable.WhereSpecifications;
import io.micronaut.data.model.*;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.AssociationQuery;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

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
                    aq, queryState, association, associationCriteria, associationCriteriaList
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
            PropertyPath prop = validateProperty(queryState, name, QueryModel.Equals.class);
            if (eq.isIgnoreCase()) {
                appendCaseInsensitiveCriterion(
                        queryState,
                        eq,
                        prop.getProperty(),
                        prop.getPath(),
                        "="
                );
            } else {
                appendCriteriaForOperator(
                        queryState,
                        prop.getProperty(),
                        prop.getPath(),
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

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.EqualsProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.EqualsProperty.class);
            appendPropertyComparison(
                    queryState.getWhereClause(),
                    queryState.getCurrentAlias(),
                    propertyName,
                    otherProperty,
                    "="
            );
        });

        queryHandlers.put(QueryModel.NotEqualsProperty.class, (queryState, criterion) -> {
            final PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.NotEqualsProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.NotEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getCurrentAlias(), propertyName, otherProperty, "!=");
        });

        queryHandlers.put(QueryModel.GreaterThanProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.GreaterThanProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.GreaterThanProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getCurrentAlias(), propertyName, otherProperty, ">");
        });

        queryHandlers.put(QueryModel.GreaterThanEqualsProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.GreaterThanEqualsProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.GreaterThanEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getCurrentAlias(), propertyName, otherProperty, ">=");
        });

        queryHandlers.put(QueryModel.LessThanProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.LessThanProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.LessThanProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState.getCurrentAlias(), propertyName, otherProperty, "<");
        });

        queryHandlers.put(QueryModel.LessThanEqualsProperty.class, (queryState, criterion) -> {
            PersistentEntity entity = queryState.getEntity();
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.LessThanEqualsProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.LessThanEqualsProperty.class);
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
            PropertyPath prop = validateProperty(queryState, name, QueryModel.NotEquals.class);
            if (eq.isIgnoreCase()) {
                appendCaseInsensitiveCriterion(
                        queryState,
                        eq,
                        prop.getProperty(),
                        prop.getPath(),
                        "!="
                );
            } else {
                appendCriteriaForOperator(
                        queryState, prop.getProperty(), prop.getPath(), eq.getValue(), " != "
                );
            }
        });

        queryHandlers.put(QueryModel.GreaterThan.class, (queryState, criterion) -> {
            QueryModel.GreaterThan eq = (QueryModel.GreaterThan) criterion;
            final String name = eq.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.GreaterThan.class);
            appendCriteriaForOperator(
                    queryState, prop.getProperty(), prop.getPath(), eq.getValue(), " > "
            );
        });

        queryHandlers.put(QueryModel.LessThanEquals.class, (queryState, criterion) -> {
            QueryModel.LessThanEquals eq = (QueryModel.LessThanEquals) criterion;
            final String name = eq.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.LessThanEquals.class);
            appendCriteriaForOperator(
                    queryState, prop.getProperty(), prop.getPath(), eq.getValue(), " <= "
            );
        });

        queryHandlers.put(QueryModel.GreaterThanEquals.class, (queryState, criterion) -> {
            QueryModel.GreaterThanEquals eq = (QueryModel.GreaterThanEquals) criterion;
            final String name = eq.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.GreaterThanEquals.class);
            appendCriteriaForOperator(
                    queryState, prop.getProperty(), prop.getPath(), eq.getValue(), " >= "
            );
        });

        queryHandlers.put(QueryModel.Between.class, (queryState, criterion) -> {
            QueryModel.Between between = (QueryModel.Between) criterion;
            final String name = between.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.Between.class);
            final String qualifiedName = queryState.getCurrentAlias() + DOT + (computePropertyPaths() ? getColumnName(prop.property) : prop.getPath());
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
            PropertyPath prop = validateProperty(queryState, name, QueryModel.LessThan.class);
            appendCriteriaForOperator(
                    queryState, prop.getProperty(), prop.getPath(), eq.getValue(), " < "
            );
        });

        queryHandlers.put(QueryModel.Like.class, (queryState, criterion) -> {
            QueryModel.Like eq = (QueryModel.Like) criterion;
            final String name = eq.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.Like.class);
            appendCriteriaForOperator(
                    queryState, prop.getProperty(), prop.getPath(), eq.getValue(), " like "
            );
        });

        queryHandlers.put(QueryModel.ILike.class, (queryState, criterion) -> {
            QueryModel.ILike eq = (QueryModel.ILike) criterion;
            final String name = eq.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.ILike.class);
            String operator = "like";
            appendCaseInsensitiveCriterion(queryState, eq, prop.getProperty(), prop.getPath(), operator);
        });

        queryHandlers.put(QueryModel.StartsWith.class, (queryState, criterion) -> {
            QueryModel.StartsWith eq = (QueryModel.StartsWith) criterion;
            appendLikeComparison(queryState, eq, formatStartsWithBeginning(), formatEndsWith());
        });

        queryHandlers.put(QueryModel.Contains.class, (queryState, criterion) -> {
            QueryModel.Contains eq = (QueryModel.Contains) criterion;
            appendLikeComparison(queryState, eq, formatStartsWith(), formatEndsWith());
        });

        queryHandlers.put(QueryModel.EndsWith.class, (queryState, criterion) -> {
            QueryModel.EndsWith eq = (QueryModel.EndsWith) criterion;
            appendLikeComparison(queryState, eq, formatStartsWith(), formEndsWithEnd());
        });

        queryHandlers.put(QueryModel.In.class, (queryState, criterion) -> {
            QueryModel.In inQuery = (QueryModel.In) criterion;
            final String name = inQuery.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.In.class);
            Object value = inQuery.getValue();
            if (value instanceof QueryParameter) {
                QueryParameter queryParameter = (QueryParameter) value;
                String currentAlias = queryState.getCurrentAlias();
                final String qualifiedName;
                if (currentAlias != null) {
                    qualifiedName = currentAlias + DOT + (computePropertyPaths() ? getColumnName(prop.getProperty()) : prop.getPath());
                } else {
                    qualifiedName = (computePropertyPaths() ? getColumnName(prop.getProperty()) : prop.getPath());
                }
                Placeholder placeholder = queryState.newParameter();
                String queryParameterName = queryParameter.getName();
                queryState.getParameters().put(placeholder.key, queryParameterName);
                queryState.addParameterType(queryParameterName, prop.getProperty().getDataType());
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

    /**
     * @return Formats end of an ends with expression
     */
    protected String formEndsWithEnd() {
        return ")";
    }

    /**
     * @return Formats the beginning of an starts with expression
     */
    protected String formatStartsWithBeginning() {
        return " LIKE CONCAT(";
    }

    /**
     * @return Formats the ends with expression.
     */
    protected String formatEndsWith() {
        return ",'%')";
    }

    /**
     * @return Formats the starts with expression.
     */
    protected String formatStartsWith() {
        return " LIKE CONCAT('%',";
    }

    private void appendEmptyExpression(QueryState queryState, String charSequencePrefix, String charSequenceSuffix, String listSuffix, String name) {
        PropertyPath property = validateProperty(queryState, name, QueryModel.IsEmpty.class);
        PersistentProperty persistentProperty = property.getProperty();
        String aliasRef = computePropertyPaths() ? getColumnName(persistentProperty) : property.getPath();
        String currentAlias = queryState.getCurrentAlias();
        String path = currentAlias == null ? "" : currentAlias + DOT;
        if (persistentProperty.isAssignable(CharSequence.class)) {

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
        PropertyPath prop = validateProperty(queryState, name, QueryModel.ILike.class);
        String currentAlias = queryState.getCurrentAlias();
        final String qualifiedName;
        if (currentAlias != null) {
            qualifiedName = currentAlias + DOT + (computePropertyPaths() ? getColumnName(prop.getProperty()) : prop.getPath());
        } else {
            qualifiedName = (computePropertyPaths() ? getColumnName(prop.getProperty()) : prop.getPath());
        }
        Placeholder parameterName = queryState.newParameter();
        queryState.getWhereClause()
                .append(qualifiedName)
                .append(prefix)
                .append(parameterName.name)
                .append(suffix);
        Object value = eq.getValue();
        addComputedParameter(queryState, prop.getProperty(), parameterName, value);
    }

    private void addComputedParameter(QueryState queryState, PersistentProperty property, Placeholder placeholder, Object queryValue) {
        if (queryValue instanceof QueryParameter) {
            String queryParameter = ((QueryParameter) queryValue).getName();
            queryState.addParameterType(queryParameter, property.getDataType());
            queryState.getParameters().put(placeholder.key, queryParameter);
        }
    }

    private void applyPropertyExpression(QueryState queryState, QueryModel.PropertyNameCriterion propertyNameCriterion, Class<?> criterionType, String expression) {
        final String name = propertyNameCriterion.getProperty();
        PropertyPath prop = validateProperty(queryState, name, criterionType);
        String alias = queryState.getCurrentAlias();
        StringBuilder whereClause = queryState.getWhereClause();
        if (alias != null) {
            whereClause.append(alias)
                    .append(DOT);
        }

        whereClause.append(computePropertyPaths() ? getColumnName(prop.getProperty()) : prop.getPath())
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

    @Override
    public QueryResult buildQuery(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("query", query);
        QueryState queryState = newQueryState(query, true);
        queryState.getQuery().append(SELECT_CLAUSE);

        buildSelectClause(query, queryState);
        QueryModel.Junction criteria = query.getCriteria();

        Collection<JoinPath> joinPaths = query.getJoinPaths();
        for (JoinPath joinPath : joinPaths) {
            queryState.applyJoin(joinPath);
        }
        Map<String, String> parameters = null;
        if (!criteria.isEmpty() || annotationMetadata.hasStereotype(WhereSpecifications.class) || queryState.getEntity().getAnnotationMetadata().hasStereotype(WhereSpecifications.class)) {
            parameters = buildWhereClause(annotationMetadata, criteria, queryState);
        }

        appendOrder(query, queryState);
        return QueryResult.of(
                queryState.getQuery().toString(),
                parameters,
                queryState.getParameterTypes(),
                queryState.getAdditionalRequiredParameters()
        );
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
     * @param joinPath The join path
     * @return The alias
     */
    public String getAliasName(JoinPath joinPath) {
        return joinPath.getAlias().orElseGet(() -> {
            String joinPathAlias = getPathOnlyAliasName(joinPath);
            PersistentEntity owner = joinPath.getAssociationPath()[0].getOwner();
            String ownerAlias = getAliasName(owner);
            if (ownerAlias.endsWith("_") && joinPathAlias.startsWith("_")) {
                return ownerAlias + joinPathAlias.substring(1);
            } else {
                return ownerAlias + joinPathAlias;
            }
        });
    }

    /**
     * Get the alias name for just the join path.
     *
     * @param joinPath The join path
     * @return The alias
     */
    @NonNull
    protected String getPathOnlyAliasName(JoinPath joinPath) {
        return joinPath.getAlias().orElseGet(() -> {
                    String p = joinPath.getPath().replace('.', '_');
                    return '_' + NamingStrategy.DEFAULT.mappedName(p) + "_";
        });
    }

    /**
     * Build a join expression for the given alias, association, join type and builder.
     *
     * @param alias            The alias
     * @param joinPath         The join path
     * @param joinType         The join type string
     * @param stringBuilder    The target builder
     * @param appliedJoinPaths The applied joins paths
     * @return An array representing the aliases for each join association in the specified join path
     */
    protected abstract String[] buildJoin(String alias, JoinPath joinPath, String joinType, StringBuilder stringBuilder, Map<String, String> appliedJoinPaths);

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
     * @param queryState The query state
     */
    protected abstract void selectAllColumns(QueryState queryState);

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
        boolean escape = shouldEscape(entity);
        StringBuilder queryString = queryState.getQuery();
        buildSelect(
                queryState,
                queryString,
                query.getProjections(),
                logicalName,
                entity
        );

        String tableName = getTableName(entity);
        if (escape) {
            tableName = quote(tableName);
        }
        queryString.append(FROM_CLAUSE)
                .append(tableName)
                .append(getTableAsKeyword())
                .append(logicalName);
    }

    /**
     * Whether queries should be escaped for the given entity.
     * @param entity The entity
     * @return True if they should be escaped
     */
    protected boolean shouldEscape(@NonNull PersistentEntity entity) {
        return entity.getAnnotationMetadata().booleanValue(MappedEntity.class, "escape").orElse(true);
    }

    /**
     * Get the AS keyword to use for table aliases.
     * @return The AS keyword if any
     */
    protected String getTableAsKeyword() {
        return AS_CLAUSE;
    }

    /**
     * Quote a column name for the dialect.
     * @param persistedName The persisted name.
     * @return The quoted name
     */
    protected String quote(String persistedName) {
        return "\"" + persistedName + "\"";
    }

    private void buildSelect(QueryState queryState, StringBuilder queryString, List<QueryModel.Projection> projectionList, String logicalName, PersistentEntity entity) {
        if (projectionList.isEmpty()) {
            selectAllColumns(queryState);
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
                    appendPropertyProjection(queryString, logicalName, identity, identity.getName());
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
                        String propertyName = pp.getPropertyName();
                        PersistentProperty persistentProperty = entity.getPropertyByPath(propertyName)
                                .orElseThrow(() -> new IllegalArgumentException("Cannot project on non-existent property: " + propertyName));
                        appendPropertyProjection(queryString, logicalName, persistentProperty, propertyName);
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

    private void appendPropertyProjection(StringBuilder queryString, String alias, PersistentProperty persistentProperty, String propertyName) {
        PersistentEntity owner = persistentProperty.getOwner();
        boolean escape = shouldEscape(owner);
        if (persistentProperty instanceof Embedded) {
            PersistentEntity embedded = ((Embedded) persistentProperty).getAssociatedEntity();
            Iterator<? extends PersistentProperty> embeddedIterator = embedded.getPersistentProperties().iterator();
            while (embeddedIterator.hasNext()) {
                PersistentProperty embeddedProp = embeddedIterator.next();
                String columnName = computeEmbeddedName(persistentProperty, persistentProperty.getName(), embeddedProp);
                if (escape) {
                    columnName = quote(columnName);
                }
                queryString.append(alias)
                        .append(DOT)
                        .append(columnName);

                if (embeddedIterator.hasNext()) {
                    queryString.append(COMMA).append(SPACE);
                }
            }
        } else {
            if (computePropertyPaths()) {
                String columnName = getColumnName(persistentProperty);
                if (escape) {
                    columnName = quote(columnName);
                }
                queryString.append(alias)
                        .append(DOT)
                        .append(columnName);
            } else {
                queryString.append(alias)
                        .append(DOT)
                        .append(propertyName);
            }

        }
    }

    private void appendProjection(
            PersistentEntity entity,
            String functionName,
            QueryModel.PropertyProjection propertyProjection,
            String logicalName,
            StringBuilder queryString) {
        boolean escape = shouldEscape(entity);
        PersistentProperty persistentProperty = entity.getPropertyByPath(propertyProjection.getPropertyName())
                .orElseThrow(() -> new IllegalArgumentException("Cannot project on non-existent property: " + propertyProjection.getPropertyName()));
        String columnName = getColumnName(persistentProperty);
        if (escape) {
            columnName = quote(columnName);
        }
        queryString.append(functionName)
                .append(OPEN_BRACKET)
                .append(logicalName)
                .append(DOT)
                .append(columnName)
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
            AssociationQuery associationQuery,
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

        String associationPath = associationQuery.getPath();
        if (!computePropertyPaths()) {
            try {
                QueryModel queryModel = queryState.getQueryModel();
                JoinPath joinPath = queryModel.getJoinPath(associationPath).orElse(null);
                if (joinPath == null && association.isForeignKey()) {
                    joinPath = queryModel.join(associationPath, association, Join.Type.DEFAULT, null);
                }

                String alias;
                if (joinPath != null) {
                    alias = queryState.applyJoin(joinPath);
                } else {
                    alias = queryState.computeAlias(associationPath);
                }
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
        } else {
            QueryModel queryModel = queryState.getQueryModel();
            JoinPath joinPath = queryModel.getJoinPath(associationPath).orElse(null);
            if (joinPath == null) {
                joinPath = queryModel.join(associationPath, association, Join.Type.DEFAULT, null);
            }

            final boolean isEmbedded = association instanceof Embedded;
            String alias = isEmbedded ? queryState.getCurrentAlias() : queryState.applyJoin(joinPath);

            try {
                queryState.setEntity(associatedEntity);
                queryState.setCurrentAlias(alias);
                if (isEmbedded) {
                    queryState.setCurrentEmbedded((Embedded) association);
                }
                buildWhereClauseForCriterion(
                        queryState,
                        associationCriteria,
                        associationCriteriaList
                );
            } finally {
                if (isEmbedded) {
                    queryState.setCurrentEmbedded(null);
                }
                queryState.setCurrentAlias(currentName);
                queryState.setEntity(currentEntity);
            }
        }

    }

    private Map<String, String> buildWhereClause(
            AnnotationMetadata annotationMetadata,
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
            final String additionalWhere = buildAdditionalWhereString(queryState.getEntity(), annotationMetadata);
            Matcher matcher = QueryBuilder.VARIABLE_PATTERN.matcher(additionalWhere);

            while (matcher.find()) {
                String name = matcher.group(2);
                queryState.addRequiredParameters(name);
                final Placeholder ph = queryState.newParameter();
                queryState.getParameters().put(ph.getKey(), ph.getName());
            }
            if (!whereStr.equals(WHERE_CLAUSE + OPEN_BRACKET)) {
                final StringBuilder queryBuilder = queryState.getQuery();
                queryBuilder.append(whereStr);
                if (StringUtils.isNotEmpty(additionalWhere)) {
                    queryBuilder.append(LOGICAL_AND).append(OPEN_BRACKET).append(additionalWhere).append(CLOSE_BRACKET);
                }
                queryBuilder.append(CLOSE_BRACKET);
            }
        } else {
            final String additionalWhereString = buildAdditionalWhereString(queryState.getEntity(), annotationMetadata);
            if (StringUtils.isNotEmpty(additionalWhereString)) {
                final StringBuilder whereClause = queryState.getWhereClause();
                whereClause.append(WHERE_CLAUSE)
                           .append(OPEN_BRACKET)
                           .append(additionalWhereString)
                           .append(CLOSE_BRACKET);
                queryState.getQuery().append(whereClause.toString());
            }
        }
        return queryState.getParameters();
    }

    private String buildAdditionalWhereString(PersistentEntity entity, AnnotationMetadata annotationMetadata) {
        final String whereStr = resolveWhereForAnnotationMetadata(annotationMetadata);
        if (StringUtils.isNotEmpty(whereStr)) {
            return whereStr;
        } else {
            return resolveWhereForAnnotationMetadata(entity.getAnnotationMetadata());
        }
    }

    private String resolveWhereForAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.getAnnotationValuesByType(Where.class)
                .stream()
                .map(av -> av.stringValue().orElse(null))
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.joining(LOGICAL_AND));
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

                String property = order.getProperty();
                PropertyPath propertyPath = validateProperty(queryState, property, Sort.Order.class);
                buff.append(getColumnName(propertyPath.getProperty()))
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
                            ac,
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
        if (value instanceof QueryParameter) {
            QueryParameter queryParameter = (QueryParameter) value;
            StringBuilder whereClause = queryState.getWhereClause();
            String alias = queryState.getCurrentAlias();
            if (property instanceof Embedded) {
                PersistentEntity embeddedEntity = ((Embedded) property).getAssociatedEntity();
                Iterator<? extends PersistentProperty> iterator = embeddedEntity.getPersistentProperties().iterator();
                while (iterator.hasNext()) {
                    PersistentProperty embeddedProperty = iterator.next();
                    Placeholder placeholder = queryState.newParameter();
                    if (alias != null) {
                        whereClause.append(alias)
                                .append(DOT);
                    }

                    String columnName = computeEmbeddedName(property, path, embeddedProperty);
                    whereClause.append(columnName)
                            .append(operator)
                            .append(placeholder.name);
                    addComputedParameter(queryState, property, placeholder, new QueryParameter(queryParameter.getName() + "." + embeddedProperty.getName()));
                    if (iterator.hasNext()) {
                        whereClause.append(LOGICAL_AND);
                    }
                }
            } else {
                Placeholder placeholder = queryState.newParameter();
                if (alias != null) {
                    whereClause.append(alias)
                            .append(DOT);
                }

                boolean computePropertyPaths = computePropertyPaths();
                if (computePropertyPaths) {
                    String columnName;
                    final Embedded currentEmbedded = queryState.getCurrentEmbedded();
                    if (currentEmbedded != null) {
                        columnName = computeEmbeddedName(
                                currentEmbedded,
                                path,
                                property
                        );
                    } else {
                        columnName = getColumnName(property);
                    }
                    if (queryState.shouldEscape()) {
                        columnName = quote(columnName);
                    }
                    whereClause.append(columnName)
                            .append(operator)
                            .append(placeholder.name);
                } else {

                    whereClause.append(path)
                            .append(operator)
                            .append(placeholder.name);
                }
                addComputedParameter(queryState, property, placeholder, queryParameter);
            }
        }
    }

    private String computeEmbeddedName(PersistentProperty parentProperty, String path, PersistentProperty embeddedProperty) {
        String explicitColumn = embeddedProperty.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
        if (explicitColumn == null) {
            NamingStrategy namingStrategy = parentProperty.getOwner().getNamingStrategy();
            explicitColumn = namingStrategy.mappedName(parentProperty.getName() + embeddedProperty.getCapitilizedName());
        }
        return computePropertyPaths() ? explicitColumn : path + "." + embeddedProperty.getName();
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

        boolean isComputePaths = computePropertyPaths();
        if (isComputePaths) {
            String columnName = getColumnName(prop);
            if (queryState.shouldEscape()) {
                columnName = quote(columnName);
            }
            whereClause.append(columnName);
        } else {
            whereClause.append(path);
        }
        whereClause.append(") ")
                .append(operator)
                .append(" lower(")
                .append(placeholder.name)
                .append(")");

        Object value = criterion.getValue();
        addComputedParameter(queryState, prop, placeholder, value);
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

            queryState.addParameterType(propertyName, prop.getDataType());
            String currentAlias = queryState.getCurrentAlias();
            if (currentAlias != null) {
                queryString.append(currentAlias).append(DOT);
            }
            String columnName = getColumnName(prop);
            if (queryState.escape) {
                columnName = quote(columnName);
            }
            queryString.append(columnName).append('=');
            Placeholder param = queryState.newParameter();
            appendUpdateSetParameter(queryString, prop, param);
            parameters.put(param.key, prop.getName());
            if (iterator.hasNext()) {
                queryString.append(COMMA);
            }
        }
    }

    /**
     * Appends the SET=? call to the query string.
     * @param queryString The query string
     * @param prop The property
     * @param param the parameter
     */
    protected void appendUpdateSetParameter(StringBuilder queryString, PersistentProperty prop, Placeholder param) {
        queryString.append(prop.getAnnotationMetadata().stringValue(DataTransformer.class, "write").orElse(param.name));
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

    private @NonNull
    PropertyPath validateProperty(QueryState queryState, String name, Class criterionType) {
        PersistentEntity entity = queryState.getEntity();
        PersistentProperty identity = entity.getIdentity();
        if (identity != null && identity.getName().equals(name)) {
            return new PropertyPath(identity, identity.getName());
        }
        PersistentProperty[] compositeIdentity = entity.getCompositeIdentity();
        if (compositeIdentity != null) {
            for (PersistentProperty property : compositeIdentity) {
                if (property.getName().equals(name)) {
                    return new PropertyPath(property, property.getName());
                }
            }
        }
        PersistentProperty prop = entity.getPropertyByName(name);
        String path = name.contains(".") ? name : entity.getPath(name).orElse(null);
        if (prop == null) {
            prop = path != null ? entity.getPropertyByPath(path).orElse(null) : null;
        }
        if (prop == null) {

            // special case handling for ID
            if (name.equals("id") && identity != null) {
                return new PropertyPath(identity, identity.getName());
            } else {
                if (criterionType == Sort.Order.class) {
                    throw new IllegalArgumentException("Cannot order on non-existent property path: " + name);

                } else {
                    throw new IllegalArgumentException("Cannot use [" +
                            criterionType.getSimpleName() + "] criterion on non-existent property path: " + name);
                }
            }
        }

        if (computePropertyPaths() && name.contains(".")) {
            StringTokenizer tokenizer = new StringTokenizer(name, ".");
            String first = tokenizer.nextToken();
            PersistentProperty p = queryState.getEntity().getPropertyByName(first);
            if (p instanceof Association) {
                QueryModel queryModel = queryState.getQueryModel();
                JoinPath joinPath = queryModel.getJoinPath(name).orElse(null);
                if (joinPath == null) {
                    joinPath = queryModel.join(p.getName(), (Association) p, Join.Type.DEFAULT, null);
                }
                if (queryState.isAllowJoins()) {
                    String alias = queryState.applyJoin(joinPath);
                    queryState.setCurrentAlias(alias);
                } else {
                    throw new IllegalArgumentException("Joins are not allowed for batch update queries");
                }
            }
        }
        return new PropertyPath(prop, path != null ? path : name);
    }

    /**
     * Whether property path expressions require computation by the implementation. In a certain query dialects
     * property paths are supported (such as JPA-QL where you can do select foo.bar) whilst for explicit SQL queries paths like
     * this have to be computed into aliases / column name references.
     *
     * @return True if property path computation is required.
     */
    protected abstract boolean computePropertyPaths();

    @Override
    public QueryResult buildUpdate(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query, @NonNull List<String> propertiesToUpdate) {
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
        String tableName = getTableName(entity);
        if (queryState.escape) {
            tableName = quote(tableName);
        }
        queryString.append(UPDATE_CLAUSE)
                .append(tableName);
        if (currentAlias != null) {
            queryString.append(SPACE)
                    .append(currentAlias);
        }
        buildUpdateStatement(queryState, propertiesToUpdate);
        buildWhereClause(annotationMetadata, query.getCriteria(), queryState);
        return QueryResult.of(
                queryString.toString(),
                queryState.getParameters(),
                queryState.getParameterTypes(),
                queryState.getAdditionalRequiredParameters()
        );
    }

    @Override
    public QueryResult buildDelete(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query) {
        PersistentEntity entity = query.getPersistentEntity();
        QueryState queryState = newQueryState(query, false);
        StringBuilder queryString = queryState.getQuery();
        String currentAlias = queryState.getCurrentAlias();
        if (!isAliasForBatch()) {
            currentAlias = null;
            queryState.setCurrentAlias(null);
        }
        StringBuilder buffer = appendDeleteClause(queryString);
        String tableName = getTableName(entity);
        if (queryState.escape) {
            tableName = quote(tableName);
        }
        buffer.append(tableName).append(SPACE);
        if (currentAlias != null) {
            buffer.append(getTableAsKeyword())
                    .append(currentAlias);
        }
        buildWhereClause(annotationMetadata, query.getCriteria(), queryState);
        return QueryResult.of(
                queryString.toString(),
                queryState.getParameters(),
                queryState.getParameterTypes(),
                queryState.getAdditionalRequiredParameters()
        );
    }

    /**
     * Should aliases be used in batch statements.
     *
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


        StringBuilder buff = new StringBuilder(ORDER_BY_CLAUSE);
        Iterator<Sort.Order> i = orders.iterator();
        while (i.hasNext()) {
            Sort.Order order = i.next();
            String property = order.getProperty();
            PersistentProperty persistentProperty = entity.getPropertyByPath(property)
                    .orElseThrow(() -> new IllegalArgumentException("Cannot sort on non-existent property path: " + property));

            String aliasName;
            if (persistentProperty instanceof Association) {
                Association association = (Association) persistentProperty;

                aliasName = getAliasName(new JoinPath(property, new Association[]{association}, Join.Type.DEFAULT, null));
            } else {
                aliasName = getAliasName(entity);
            }
            buff.append(aliasName)
                    .append(DOT)
                    .append(getColumnName(persistentProperty))
                    .append(SPACE)
                    .append(order.getDirection());
            if (i.hasNext()) {
                buff.append(",");
            }
        }

        return QueryResult.of(
                buff.toString(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptySet()
        );
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
    @Internal
    protected final class QueryState {
        private final Map<String, String> appliedJoinPaths = new HashMap<>();
        private final AtomicInteger position = new AtomicInteger(0);
        private final Map<String, String> parameters = new LinkedHashMap<>();
        private final Map<String, DataType> parameterTypes = new LinkedHashMap<>();
        private final StringBuilder query = new StringBuilder();
        private final StringBuilder whereClause = new StringBuilder();
        private final boolean allowJoins;
        private final QueryModel queryObject;
        private final boolean escape;
        private String currentAlias;
        private PersistentEntity entity;
        private Embedded currentEmbedded;
        private Set<String> additionalRequiredParameters;

        private QueryState(QueryModel query, boolean allowJoins) {
            this.allowJoins = allowJoins;
            this.queryObject = query;
            this.entity = query.getPersistentEntity();
            this.escape = AbstractSqlLikeQueryBuilder.this.shouldEscape(entity);
            this.currentAlias = getAliasName(entity);
        }

        /**
         * @return The current current alias
         */
        public @Nullable
        String getCurrentAlias() {
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
         * @return The current embedded property
         */
        @Nullable
        public Embedded getCurrentEmbedded() {
            return currentEmbedded;
        }

        /**
         * @param currentEmbedded The current embedded
         */
        public void setCurrentEmbedded(@Nullable Embedded currentEmbedded) {
            this.currentEmbedded = currentEmbedded;
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
         * @return The precomputed parameter types
         */
        public Map<String, DataType> getParameterTypes() {
            return Collections.unmodifiableMap(parameterTypes);
        }

        /**
         * Add a parameter type.
         * @param name The name
         * @param dataType The type
         */
        public void addParameterType(@NonNull String name, @NonNull DataType dataType) {
            this.parameterTypes.put(name, dataType);
        }

        /**
         * Add a required parameter.
         * @param name The name
         */
        public void addRequiredParameters(@NonNull String name) {
            if (additionalRequiredParameters == null) {
                additionalRequiredParameters = new HashSet<>(5);
            }
            additionalRequiredParameters.add(name);
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
        public QueryModel getQueryModel() {
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
         * @param joinPath The join path
         * @return The alias
         */
        public String applyJoin(@NonNull JoinPath joinPath) {
            String alias = getCurrentAlias();
            StringBuilder associationPath = new StringBuilder(alias + DOT + joinPath.getPath());
            if (!appliedJoinPaths.containsKey(associationPath.toString())) {
                Optional<JoinPath> jp = getQueryModel().getJoinPath(joinPath.getPath());
                if (jp.isPresent()) {
                    joinPath = jp.get();
                }
                StringBuilder stringBuilder = getQuery();
                Join.Type jt = joinPath.getJoinType();
                String joinType = resolveJoinType(jt);


                String[] associationAlias = buildJoin(alias, joinPath, joinType, stringBuilder, appliedJoinPaths);
                Association[] associationArray = joinPath.getAssociationPath();
                associationPath = null;
                for (int i = 0; i < associationAlias.length; i++) {
                    Association association = associationArray[i];
                    if (associationPath == null) {
                        associationPath = new StringBuilder(association.getName());
                    } else {
                        associationPath.append(DOT).append(association.getName());
                    }
                    String computedAlias = associationAlias[i];
                    appliedJoinPaths.put(alias + DOT + associationPath, computedAlias);
                }
                return associationAlias[associationAlias.length - 1];
            } else {
                return appliedJoinPaths.get(associationPath.toString());
            }
        }

        /**
         * Computes the alias for the given association path given the current state of the joins.
         *
         * @param associationPath The assocation path.
         * @return The alias
         */
        public @NonNull String computeAlias(String associationPath) {
            String name = getCurrentAlias() + DOT + associationPath;
            if (appliedJoinPaths.containsKey(name)) {
                return appliedJoinPaths.get(name);
            } else {
                int i = associationPath.indexOf('.');
                if (i > -1) {
                    String p = getCurrentAlias() + DOT + associationPath.substring(0, i);
                    if (appliedJoinPaths.containsKey(p)) {
                        return appliedJoinPaths.get(p) + DOT + associationPath.substring(i + 1);
                    }
                }
            }
            return getCurrentAlias() + DOT + associationPath;
        }

        /**
         * @return Should escape the query
         */
        public boolean shouldEscape() {
            return escape;
        }

        /**
         * The additional required parameters.
         * @return The parameters
         */
        public @Nullable Set<String> getAdditionalRequiredParameters() {
            return this.additionalRequiredParameters;
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

    /**
     * Represents a path to a property.
     */
    protected class PropertyPath {
        private final PersistentProperty property;
        private final String path;

        /**
         * Default constructor.
         *
         * @param property The property
         * @param path     The path
         */
        public PropertyPath(@NonNull PersistentProperty property, @NonNull String path) {
            this.property = property;
            this.path = path;
        }

        /**
         * @return The property
         */
        public @NonNull
        PersistentProperty getProperty() {
            return property;
        }

        /**
         * @return The path
         */
        public @NonNull
        String getPath() {
            return path;
        }
    }
}
