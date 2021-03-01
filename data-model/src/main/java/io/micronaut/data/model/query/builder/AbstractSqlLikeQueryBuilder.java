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
    protected static final String AND = "AND";
    protected static final String LOGICAL_AND = " " + AND + " ";
    protected static final String UPDATE_CLAUSE = "UPDATE ";
    protected static final String DELETE_CLAUSE = "DELETE ";
    protected static final String OR = "OR";
    protected static final String LOGICAL_OR = " " + OR + " ";
    protected static final String FUNCTION_COUNT = "COUNT";
    protected static final String AVG = "AVG";
    protected static final String DISTINCT = "DISTINCT";
    protected static final String SUM = "SUM";
    protected static final String MIN = "MIN";
    protected static final String MAX = "MAX";
    protected static final String COUNT_DISTINCT = "COUNT(DISTINCT";
    protected static final String IS_NOT_NULL = " IS NOT NULL ";
    protected static final String IS_EMPTY = " IS EMPTY ";
    protected static final String IS_NOT_EMPTY = " IS NOT EMPTY ";
    protected static final String IS_NULL = " IS NULL ";
    protected static final String EQUALS_TRUE = " = TRUE ";
    protected static final String EQUALS_FALSE = " = FALSE ";
    protected static final String GREATER_THAN_OR_EQUALS = " >= ";
    protected static final String LESS_THAN_OR_EQUALS = " <= ";
    protected static final String LESS_THAN = " < ";
    protected static final String GREATER_THAN = " > ";
    protected static final String NOT_EQUALS = " != ";
    protected static final String ALIAS_REPLACE = "@.";
    protected static final String ALIAS_REPLACE_QUOTED = Matcher.quoteReplacement(ALIAS_REPLACE);
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
            QueryModel.EqualsProperty eq = (QueryModel.EqualsProperty) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.EqualsProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.EqualsProperty.class);
            appendPropertyComparison(
                    queryState.getWhereClause(),
                    queryState,
                    left,
                    right,
                    "="
            );
        });

        queryHandlers.put(QueryModel.NotEqualsProperty.class, (queryState, criterion) -> {
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.NotEqualsProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.NotEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState, left, right, "!=");
        });

        queryHandlers.put(QueryModel.GreaterThanProperty.class, (queryState, criterion) -> {
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.GreaterThanProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.GreaterThanProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState, left, right, ">");
        });

        queryHandlers.put(QueryModel.GreaterThanEqualsProperty.class, (queryState, criterion) -> {
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.GreaterThanEqualsProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.GreaterThanEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState, left, right, ">=");
        });

        queryHandlers.put(QueryModel.LessThanProperty.class, (queryState, criterion) -> {
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.LessThanProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.LessThanProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState, left, right, "<");
        });

        queryHandlers.put(QueryModel.LessThanEqualsProperty.class, (queryState, criterion) -> {
            QueryModel.PropertyComparisonCriterion eq = (QueryModel.PropertyComparisonCriterion) criterion;
            final String propertyName = eq.getProperty();
            String otherProperty = eq.getOtherProperty();

            PropertyPath left = validateProperty(queryState, propertyName, QueryModel.LessThanEqualsProperty.class);
            PropertyPath right = validateProperty(queryState, otherProperty, QueryModel.LessThanEqualsProperty.class);
            appendPropertyComparison(queryState.getWhereClause(), queryState, left, right, "<=");
        });

        queryHandlers.put(QueryModel.IsNull.class, (queryState, criterion) -> {
            QueryModel.IsNull isNull = (QueryModel.IsNull) criterion;
            applyPropertyExpression(queryState, isNull, QueryModel.IsNull.class, IS_NULL);
        });

        queryHandlers.put(QueryModel.IsTrue.class, (queryState, criterion) -> {
            QueryModel.IsTrue isTrue = (QueryModel.IsTrue) criterion;
            applyPropertyExpression(queryState, isTrue, QueryModel.IsTrue.class, EQUALS_TRUE);
        });

        queryHandlers.put(QueryModel.IsFalse.class, (queryState, criterion) -> {
            QueryModel.IsFalse isFalse = (QueryModel.IsFalse) criterion;
            applyPropertyExpression(queryState, isFalse, QueryModel.IsFalse.class, EQUALS_FALSE);
        });

        queryHandlers.put(QueryModel.IsNotNull.class, (queryState, criterion) -> {
            QueryModel.IsNotNull isNotNull = (QueryModel.IsNotNull) criterion;
            applyPropertyExpression(queryState, isNotNull, QueryModel.IsNotNull.class, IS_NOT_NULL);
        });

        queryHandlers.put(QueryModel.IsEmpty.class, (queryState, criterion) -> {
            QueryModel.IsEmpty isEmpty = (QueryModel.IsEmpty) criterion;
            final String name = isEmpty.getProperty();
            appendEmptyExpression(queryState, IS_NULL + OR + StringUtils.SPACE, " = '' ", IS_EMPTY, name);
        });

        queryHandlers.put(QueryModel.IsNotEmpty.class, (queryState, criterion) -> {
            QueryModel.IsNotEmpty isNotEmpty = (QueryModel.IsNotEmpty) criterion;
            final String name = isNotEmpty.getProperty();
            appendEmptyExpression(queryState, IS_NOT_NULL + AND + StringUtils.SPACE, " <> '' ", IS_NOT_EMPTY, name);
        });

        queryHandlers.put(QueryModel.IdEquals.class, (queryState, criterion) -> {
            PersistentProperty prop = queryState.getEntity().getIdentity();
            if (prop == null) {
                throw new IllegalStateException("No ID found for entity: " + queryState.getEntity().getName());
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
                        queryState, prop.getProperty(), prop.getPath(), eq.getValue(), NOT_EQUALS
                );
            }
        });

        queryHandlers.put(QueryModel.GreaterThan.class, (queryState, criterion) -> {
            QueryModel.GreaterThan eq = (QueryModel.GreaterThan) criterion;
            final String name = eq.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.GreaterThan.class);
            appendCriteriaForOperator(
                    queryState, prop.getProperty(), prop.getPath(), eq.getValue(), GREATER_THAN
            );
        });

        queryHandlers.put(QueryModel.LessThanEquals.class, (queryState, criterion) -> {
            QueryModel.LessThanEquals eq = (QueryModel.LessThanEquals) criterion;
            final String name = eq.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.LessThanEquals.class);
            appendCriteriaForOperator(
                    queryState, prop.getProperty(), prop.getPath(), eq.getValue(), LESS_THAN_OR_EQUALS
            );
        });

        queryHandlers.put(QueryModel.GreaterThanEquals.class, (queryState, criterion) -> {
            QueryModel.GreaterThanEquals eq = (QueryModel.GreaterThanEquals) criterion;
            final String name = eq.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.GreaterThanEquals.class);
            appendCriteriaForOperator(
                    queryState, prop.getProperty(), prop.getPath(), eq.getValue(), GREATER_THAN_OR_EQUALS
            );
        });

        queryHandlers.put(QueryModel.Between.class, (queryState, criterion) -> {
            QueryModel.Between between = (QueryModel.Between) criterion;
            final String name = between.getProperty();
            PropertyPath prop = validateProperty(queryState, name, QueryModel.Between.class);

            Placeholder fromParam = queryState.newParameter();
            Placeholder toParam = queryState.newParameter();
            StringBuilder whereClause = queryState.getWhereClause();
            whereClause.append(OPEN_BRACKET);
            appendPropertyRef(whereClause, queryState, prop.property, prop.path);
            whereClause.append(GREATER_THAN_OR_EQUALS)
                    .append(fromParam.name);
            whereClause.append(LOGICAL_AND);
            appendPropertyRef(whereClause, queryState, prop.property, prop.path);
            whereClause.append(LESS_THAN_OR_EQUALS)
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
                    queryState, prop.getProperty(), prop.getPath(), eq.getValue(), LESS_THAN
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
        StringBuilder whereClause = queryState.getWhereClause();
        if (persistentProperty.isAssignable(CharSequence.class)) {
            appendPropertyRef(whereClause, queryState, persistentProperty, property.path);
            whereClause.append(charSequencePrefix);
            appendPropertyRef(whereClause, queryState, persistentProperty, property.path);
            whereClause.append(charSequenceSuffix);
        } else {
            appendPropertyRef(whereClause, queryState, persistentProperty, property.path);
            whereClause.append(listSuffix);
        }
    }

    private void appendLikeComparison(QueryState queryState, QueryModel.PropertyCriterion eq, String prefix, String suffix) {
        final String name = eq.getProperty();
        PropertyPath prop = validateProperty(queryState, name, QueryModel.ILike.class);
        StringBuilder whereClause = queryState.getWhereClause();
        appendPropertyRef(whereClause, queryState, prop.property, prop.path);
        Placeholder parameterName = queryState.newParameter();
        whereClause.append(prefix)
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
        StringBuilder whereClause = queryState.getWhereClause();
        appendPropertyRef(whereClause, queryState, prop.property, prop.path);
        whereClause.append(expression);
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

        Collection<JoinPath> joinPaths = query.getJoinPaths();
        for (JoinPath joinPath : joinPaths) {
            queryState.applyJoin(joinPath);
        }

        StringBuilder select = new StringBuilder(SELECT_CLAUSE);
        buildSelectClause(query, queryState, select);
        appendForUpdate(QueryPosition.AFTER_TABLE_NAME, query, select);
        queryState.getQuery().insert(0, select.toString());

        QueryModel.Junction criteria = query.getCriteria();

        Map<String, String> parameters = null;
        if (!criteria.isEmpty() || annotationMetadata.hasStereotype(WhereSpecifications.class) || queryState.getEntity().getAnnotationMetadata().hasStereotype(WhereSpecifications.class)) {
            parameters = buildWhereClause(annotationMetadata, criteria, queryState);
        }

        appendOrder(query, queryState);
        appendForUpdate(QueryPosition.END_OF_QUERY, query, queryState.getQuery());

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
     * Get the table name for the given entity.
     *
     * @param entity The entity
     * @return The table name
     */
    protected String getUnescapedTableName(PersistentEntity entity) {
        return entity.getPersistedName();
    }

    /**
     * Get an alias name for the given entity.
     *
     * @param entity The entity
     * @return The alias name
     */
    protected String getAliasName(PersistentEntity entity) {
        return entity.getAnnotationMetadata().stringValue(MappedEntity.class, "alias")
                .orElseGet(() -> getTableName(entity) + "_");
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
                    return NamingStrategy.DEFAULT.mappedName(p) + "_";
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
     * @param queryState       The query state
     * @return An array representing the aliases for each join association in the specified join path
     */
    protected abstract String[] buildJoin(String alias, JoinPath joinPath, String joinType, StringBuilder stringBuilder, Map<String, String> appliedJoinPaths, QueryState queryState);

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
     * @param queryBuffer
     */
    protected abstract void selectAllColumns(QueryState queryState, StringBuilder queryBuffer);

    /**
     * Selects all columns for the given entity and alias.
     * @param entity The entity
     * @param alias The alias
     * @param queryBuffer The buffer to append the columns
     */
    protected abstract void selectAllColumns(PersistentEntity entity, String alias, StringBuilder queryBuffer);

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

    private void buildSelectClause(QueryModel query, QueryState queryState, StringBuilder queryString) {
        String logicalName = queryState.getCurrentAlias();
        PersistentEntity entity = queryState.getEntity();
        buildSelect(
                queryState,
                queryString,
                query.getProjections(),
                logicalName,
                entity
        );

        String tableName = getTableName(entity);
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
            selectAllColumns(queryState, queryString);
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
                    appendPropertyProjection(queryString, logicalName, entity, identity, identity.getName(), queryState);
                } else if (projection instanceof QueryModel.PropertyProjection) {
                    QueryModel.PropertyProjection pp = (QueryModel.PropertyProjection) projection;
                    String alias = pp.getAlias().orElse(null);
                    if (projection instanceof QueryModel.AvgProjection) {
                        appendProjection(queryState.getEntity(), AVG, pp, logicalName, queryString);
                    } else if (projection instanceof QueryModel.DistinctPropertyProjection) {
                        appendProjection(queryState.getEntity(), DISTINCT, pp, logicalName, queryString);
                    } else if (projection instanceof QueryModel.SumProjection) {
                        appendProjection(queryState.getEntity(), SUM, pp, logicalName, queryString);
                    } else if (projection instanceof QueryModel.MinProjection) {
                        appendProjection(queryState.getEntity(), MIN, pp, logicalName, queryString);
                    } else if (projection instanceof QueryModel.MaxProjection) {
                        appendProjection(queryState.getEntity(), MAX, pp, logicalName, queryString);
                    } else if (projection instanceof QueryModel.CountDistinctProjection) {
                        appendProjection(queryState.getEntity(), COUNT_DISTINCT, pp, logicalName, queryString);
                        queryString.append(CLOSE_BRACKET);
                    } else {
                        String propertyName = pp.getPropertyName();
                        PersistentProperty persistentProperty = entity.getPropertyByPath(propertyName)
                                .orElseThrow(() -> new IllegalArgumentException("Cannot project on non-existent property: " + propertyName));
                        appendPropertyProjection(queryString, logicalName, entity, persistentProperty, propertyName, queryState);
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

    private void appendPropertyProjection(StringBuilder queryString,
                                          String alias,
                                          PersistentEntity rootEntity,
                                          PersistentProperty persistentProperty,
                                          String propertyName,
                                          QueryState queryState) {
        PersistentEntity owner = persistentProperty.getOwner();
        boolean escape = shouldEscape(owner);
        if (persistentProperty instanceof Embedded) {
            PersistentEntity embedded = ((Embedded) persistentProperty).getAssociatedEntity();
            Iterator<? extends PersistentProperty> embeddedIterator = embedded.getPersistentProperties().iterator();
            while (embeddedIterator.hasNext()) {
                PersistentProperty embeddedProp = embeddedIterator.next();
                String columnName = computeEmbeddedName((Embedded) persistentProperty, persistentProperty.getName(), embeddedProp);
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
        } else if (persistentProperty instanceof Association) {
            PersistentEntity associatedEntity = ((Association) persistentProperty).getAssociatedEntity();
            String tableAlias = queryState.computeAlias(persistentProperty.getName());
            selectAllColumns(associatedEntity, tableAlias, queryString);
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
            String additionalWhere = buildAdditionalWhereString(queryState.getCurrentAlias(), queryState.getEntity(), annotationMetadata);
            if (StringUtils.isNotEmpty(additionalWhere)) {
                StringBuffer additionalWhereBuilder = new StringBuffer();
                Matcher matcher = QueryBuilder.VARIABLE_PATTERN.matcher(additionalWhere);
                while (matcher.find()) {
                    String name = matcher.group(3);
                    queryState.addRequiredParameters(name);
                    final Placeholder ph = queryState.newParameter();
                    queryState.getParameters().put(ph.getKey(), name);
                    matcher.appendReplacement(additionalWhereBuilder, ph.getName());
                }
                matcher.appendTail(additionalWhereBuilder);
                additionalWhere = additionalWhereBuilder.toString();
            }
            if (whereStr.equals(WHERE_CLAUSE + OPEN_BRACKET)) {
                if (StringUtils.isNotEmpty(additionalWhere)) {
                    StringBuilder queryBuilder = queryState.getQuery();
                    queryBuilder.append(whereStr).append(additionalWhere).append(CLOSE_BRACKET);
                }
            } else {
                StringBuilder queryBuilder = queryState.getQuery();
                queryBuilder.append(whereStr);
                if (StringUtils.isNotEmpty(additionalWhere)) {
                    queryBuilder.append(LOGICAL_AND).append(OPEN_BRACKET).append(additionalWhere).append(CLOSE_BRACKET);
                }
                queryBuilder.append(CLOSE_BRACKET);
            }
        } else {
            final String additionalWhereString = buildAdditionalWhereString(queryState.getCurrentAlias(), queryState.getEntity(), annotationMetadata);
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

    private String buildAdditionalWhereString(String alias, PersistentEntity entity, AnnotationMetadata annotationMetadata) {
        final String whereStr = resolveWhereForAnnotationMetadata(alias, annotationMetadata);
        if (StringUtils.isNotEmpty(whereStr)) {
            return whereStr;
        } else {
            return resolveWhereForAnnotationMetadata(alias, entity.getAnnotationMetadata());
        }
    }

    private String resolveWhereForAnnotationMetadata(String alias, AnnotationMetadata annotationMetadata) {
        return annotationMetadata.getAnnotationValuesByType(Where.class)
                .stream()
                .map(av -> av.stringValue().orElse(null))
                .map(val -> replaceAlias(alias, val))
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

    protected void appendForUpdate(QueryPosition queryPosition, QueryModel query, StringBuilder queryBuilder) {
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
            PersistentEntity rootEntity = queryState.getEntity();
            PersistentProperty rootIdentity = rootEntity.getIdentity();

            boolean computePropertyPaths = computePropertyPaths();
            if (!computePropertyPaths) {
                Placeholder placeholder = queryState.newParameter();
                appendPropertyRef(whereClause, queryState, property, path);
                whereClause.append(operator).append(placeholder.name);
                addComputedParameter(queryState, property, placeholder, queryParameter);
                return;
            }

            boolean embeddedId = rootIdentity instanceof Embedded &&  ((Embedded) rootIdentity).getAssociatedEntity() == property.getOwner();
            if (property instanceof Embedded) {
                PersistentEntity embeddedEntity = ((Embedded) property).getAssociatedEntity();
                Iterator<? extends PersistentProperty> iterator = embeddedEntity.getPersistentProperties().iterator();
                while (iterator.hasNext()) {
                    PersistentProperty embeddedProperty = iterator.next();
                    Embedded currentEmbedded = queryState.getCurrentEmbedded();
                    queryState.setCurrentEmbedded((Embedded) property);
                    appendPropertyRef(whereClause, queryState, embeddedProperty, path);
                    queryState.setCurrentEmbedded(currentEmbedded);
                    Placeholder placeholder = queryState.newParameter();
                    whereClause.append(operator).append(placeholder.name);
                    addComputedParameter(queryState, property, placeholder, new QueryParameter(queryParameter.getName() + "." + embeddedProperty.getName()));
                    if (iterator.hasNext()) {
                        whereClause.append(LOGICAL_AND);
                    }
                }
            } else if (embeddedId) {
                Embedded currentEmbedded = queryState.getCurrentEmbedded();
                queryState.setCurrentEmbedded((Embedded) rootIdentity);
                appendPropertyRef(whereClause, queryState, property, path);
                queryState.setCurrentEmbedded(currentEmbedded);
                Placeholder placeholder = queryState.newParameter();
                whereClause.append(operator).append(placeholder.name);
                addComputedParameter(queryState, property, placeholder, new QueryParameter(property.getName()));
            } else {
                Placeholder placeholder = queryState.newParameter();
                appendPropertyRef(whereClause, queryState, property, path);
                whereClause.append(operator).append(placeholder.name);
                addComputedParameter(queryState, property, placeholder, queryParameter);
            }
        } else {
            throw new IllegalStateException("Unknown value: " + value);
        }
    }

    private void appendPropertyRef(StringBuilder sb, QueryState queryState, PersistentProperty property, String path) {
        String currentAlias = queryState.getCurrentAlias();
        String readTransformer = getDataTransformerReadValue(currentAlias, property).orElse(null);
        if (readTransformer != null) {
            sb.append(readTransformer);
            return;
        }

        if (currentAlias != null) {
            sb.append(currentAlias).append(DOT);
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
            sb.append(columnName);
        } else {
            sb.append(getDataTransformerReadValue(currentAlias, property).orElse(path));
        }
    }

    private void appendCaseInsensitiveCriterion(QueryState queryState, QueryModel.PropertyCriterion criterion, PersistentProperty prop, String path, String operator) {
        Placeholder placeholder = queryState.newParameter();
        StringBuilder whereClause = queryState.getWhereClause();
        whereClause.append("lower(");
        appendPropertyRef(whereClause, queryState, prop, path);
        whereClause.append(") ")
                .append(operator)
                .append(" lower(")
                .append(placeholder.name)
                .append(")");

        Object value = criterion.getValue();
        addComputedParameter(queryState, prop, placeholder, value);
    }

    /**
     * @param parentProperty The parent association
     * @param path The path
     * @param embeddedProperty The association property
     * @return The embedded name
     */
    protected String computeEmbeddedName(Embedded parentProperty, String path, PersistentProperty embeddedProperty) {
        if (!computePropertyPaths()) {
            return path + "." + embeddedProperty.getName();
        }
        String explicitColumn = embeddedProperty.getAnnotationMetadata().stringValue(MappedProperty.class).orElse(null);
        if (explicitColumn == null) {
            NamingStrategy namingStrategy = parentProperty.getOwner().getNamingStrategy();
            return namingStrategy.mappedName(parentProperty, embeddedProperty);
        }
        return explicitColumn;
    }

    /**
     * For handling subqueries.
     *
     * @param queryState           The query state
     * @param subqueryCriterion    The subquery criterion
     * @param comparisonExpression The comparison expression
     */
    protected void handleSubQuery(QueryState queryState, QueryModel.SubqueryCriterion subqueryCriterion, String comparisonExpression) {
        PropertyPath propertyPath = validateProperty(queryState, subqueryCriterion.getProperty(), QueryModel.In.class);
        StringBuilder whereClause = queryState.getWhereClause();
        appendPropertyRef(whereClause, queryState, propertyPath.property, propertyPath.path);
        whereClause.append(comparisonExpression);
        buildSubQuery(queryState, subqueryCriterion.getValue());
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
        boolean addComma = false; // skip first comma
        while (iterator.hasNext()) {
            String propertyName = iterator.next();
            PersistentProperty prop = queryState.getEntity().getPropertyByName(propertyName);
            if (prop == null || prop.isGenerated()) {
                continue;
            }

            if (prop instanceof Association) {
                if (prop instanceof Embedded) {
                    if (isExpandEmbedded()) {

                        Embedded embedded = (Embedded) prop;
                        final String embeddedName = embedded.getName();
                        final Collection<? extends PersistentProperty> embeddedProps = embedded.getAssociatedEntity().getPersistentProperties();


                        final Iterator<? extends PersistentProperty> eIter = embeddedProps.iterator();
                        while (eIter.hasNext()) {
                            final PersistentProperty embeddedProp = eIter.next();
                            String propertyPath = embeddedName + '.' + embeddedProp.getName();
                            queryState.addParameterType(propertyPath, embeddedProp.getDataType());

                            String currentAlias = queryState.getCurrentAlias();
                            if (addComma) {
                                queryString.append(COMMA);
                            }
                            addComma = true;
                            if (currentAlias != null) {
                                queryString.append(currentAlias).append(DOT);
                            }

                            String columnName = embeddedProp.getAnnotationMetadata().stringValue(
                                    MappedProperty.class
                            ).orElseGet(() ->
                                    queryState.getEntity().getNamingStrategy()
                                        .mappedName(embedded, embeddedProp)
                            );
                            if (queryState.escape) {
                                columnName = quote(columnName);
                            }
                            queryString.append(columnName).append('=');
                            Placeholder param = queryState.newParameter();
                            appendUpdateSetParameter(queryString, currentAlias, prop, param);
                            parameters.put(param.key, propertyPath);

                        }
                        continue;
                    }
                } else if (((Association) prop).isForeignKey()) {
                    throw new IllegalArgumentException("Foreign key associations cannot be updated as part of a batch update statement");
                }
            }

            queryState.addParameterType(propertyName, prop.getDataType());
            String currentAlias = queryState.getCurrentAlias();
            if (addComma) {
                queryString.append(COMMA);
            }
            addComma = true;
            if (currentAlias != null) {
                queryString.append(currentAlias).append(DOT);
            }
            String columnName = getColumnName(prop);
            if (queryState.escape) {
                columnName = quote(columnName);
            }
            queryString.append(columnName).append('=');
            Placeholder param = queryState.newParameter();
            appendUpdateSetParameter(queryString, currentAlias, prop, param);
            parameters.put(param.key, prop.getName());
        }
    }

    /**
     * Should embedded queries by expanded by the implementation.
     * @return True if they should
     */
    protected boolean isExpandEmbedded() {
        return false;
    }

    /**
     * Appends the SET=? call to the query string.
     * @param sb The string builder
     * @param alias The alias
     * @param prop The property
     * @param param the parameter
     */
    protected void appendUpdateSetParameter(StringBuilder sb, String alias, PersistentProperty prop, Placeholder param) {
        sb.append(getDataTransformerWriteValue(alias, prop).orElse(param.name));
    }

    private void appendPropertyComparison(StringBuilder sb, QueryState queryState, PropertyPath left, PropertyPath right, String operator) {
        appendPropertyRef(sb, queryState, left.property, left.path);
        sb.append(operator);
        appendPropertyRef(sb, queryState, right.property, right.path);
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
            path = "";
            StringTokenizer tokenizer = new StringTokenizer(name, ".");
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                path = path.isEmpty() ? token : path + "." + token;
                String finalPath = path;
                prop = queryState.getEntity().getPropertyByPath(path)
                        .orElseThrow(() -> new IllegalStateException("Cannot find property at path: " + finalPath));
                if (prop instanceof Association) {
                    Association association = (Association) prop;
                    if (association.getKind() == Relation.Kind.EMBEDDED) {
                        continue;
                    } else {
                        QueryModel queryModel = queryState.getQueryModel();
                        JoinPath joinPath = queryModel.getJoinPath(name).orElse(null);
                        if (joinPath == null) {
                            joinPath = queryModel.join(prop.getName(), association, Join.Type.DEFAULT, null);
                        }
                        if (queryState.isAllowJoins()) {
                            String alias = queryState.applyJoin(joinPath);
                            queryState.setCurrentAlias(alias);
                        } else {
                            throw new IllegalArgumentException("Joins are not allowed for batch update queries");
                        }
                    }
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
                final int j = property.indexOf('.');
                if (j > -1) {
                    final String associationName = property.substring(0, j);
                    final PersistentProperty assProp = entity.getPropertyByName(associationName);
                    if (assProp instanceof Association) {
                        Association association = (Association) assProp;
                        persistentProperty = association.getAssociatedEntity().getPropertyByName(property.substring(j + 1));
                        if (persistentProperty != null) {
                            aliasName = getAliasName(
                                    new JoinPath(
                                            associationName,
                                            new Association[]{ association },
                                            Join.Type.DEFAULT,
                                            null
                                    )
                            );
                        } else {
                            throw new IllegalArgumentException("Cannot sort on non-existent property path: " + property);
                        }
                    } else {
                        throw new IllegalArgumentException("Cannot sort on non-existent property path: " + property);
                    }
                } else {
                    aliasName = getAliasName(entity);
                }
            }
            boolean ignoreCase = order.isIgnoreCase();
            if (ignoreCase) {
                buff.append("LOWER(");
            }
            buff.append(aliasName)
                    .append(DOT);
            buff.append(getColumnName(persistentProperty));
            if (ignoreCase) {
                buff.append(")");
            }
            buff.append(SPACE)
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

    private Optional<String> getDataTransformerValue(String alias, PersistentProperty prop, String val) {
        return prop.getAnnotationMetadata()
                .stringValue(DataTransformer.class, val)
                .map(v -> replaceAlias(alias, v));
    }

    private String replaceAlias(String alias, String v) {
        return v.replaceAll(ALIAS_REPLACE_QUOTED, alias == null ? "" : alias + ".");
    }

    /**
     * Returns transformed value if the data transformer id defined.
     * @param alias query table alias
     * @param prop a property
     * @return optional transformed value
     */
    protected Optional<String> getDataTransformerReadValue(String alias, PersistentProperty prop) {
        return getDataTransformerValue(alias, prop, "read");
    }

    /**
     * Returns transformed value if the data transformer id defined.
     * @param alias query table alias
     * @param prop a property
     * @return optional transformed value
     */
    protected Optional<String> getDataTransformerWriteValue(String alias, PersistentProperty prop) {
        return getDataTransformerValue(alias, prop, "write");
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


                String[] associationAlias = buildJoin(
                        alias,
                        joinPath,
                        joinType,
                        stringBuilder,
                        appliedJoinPaths,
                        this);
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
    public static final class Placeholder {
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

    protected enum QueryPosition {
        AFTER_TABLE_NAME, END_OF_QUERY
    }
}
