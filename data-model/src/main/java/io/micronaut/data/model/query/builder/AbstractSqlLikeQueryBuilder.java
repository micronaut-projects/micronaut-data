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
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DataTransformer;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.annotation.repeatable.WhereSpecifications;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.AssociationQuery;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An abstract class for builders that build SQL-like queries.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@SuppressWarnings("checkstyle:FileLength")
public abstract class AbstractSqlLikeQueryBuilder implements QueryBuilder {
    public static final String AUTO_POPULATED_PARAMETER_PREFIX = "$";
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
    protected static final String EQUALS = " = ";
    protected static final String NOT_EQUALS = " != ";
    protected static final String ALIAS_REPLACE = "@.";
    protected static final String ALIAS_REPLACE_QUOTED = Matcher.quoteReplacement(ALIAS_REPLACE);
    protected final Map<Class, CriterionHandler> queryHandlers = new HashMap<>(30);

    {
        addCriterionHandler(AssociationQuery.class, this::handleAssociationCriteria);

        addCriterionHandler(QueryModel.Negation.class, (ctx, negation) -> {
            ctx.whereClause().append(NOT_CLAUSE).append(OPEN_BRACKET);
            handleJunction(ctx, negation);
            ctx.whereClause().append(CLOSE_BRACKET);
        });

        addCriterionHandler(QueryModel.Conjunction.class, (ctx, conjunction) -> {
            ctx.whereClause().append(OPEN_BRACKET);
            handleJunction(ctx, conjunction);
            ctx.whereClause().append(CLOSE_BRACKET);
        });

        addCriterionHandler(QueryModel.Disjunction.class, (ctx, disjunction) -> {
            ctx.whereClause().append(OPEN_BRACKET);
            handleJunction(ctx, disjunction);
            ctx.whereClause().append(CLOSE_BRACKET);
        });

        addCriterionHandler(QueryModel.Equals.class, optionalCaseValueComparison(EQUALS));
        addCriterionHandler(QueryModel.NotEquals.class, optionalCaseValueComparison(NOT_EQUALS));
        addCriterionHandler(QueryModel.EqualsProperty.class, comparison("="));
        addCriterionHandler(QueryModel.NotEqualsProperty.class, comparison("!="));
        addCriterionHandler(QueryModel.GreaterThanProperty.class, comparison(">"));
        addCriterionHandler(QueryModel.GreaterThanEqualsProperty.class, comparison(">="));
        addCriterionHandler(QueryModel.LessThanProperty.class, comparison("<"));
        addCriterionHandler(QueryModel.LessThanEqualsProperty.class, comparison("<="));
        addCriterionHandler(QueryModel.IsNull.class, expression(IS_NULL));
        addCriterionHandler(QueryModel.IsTrue.class, expression(EQUALS_TRUE));
        addCriterionHandler(QueryModel.IsFalse.class, expression(EQUALS_FALSE));
        addCriterionHandler(QueryModel.IsNotNull.class, expression(IS_NOT_NULL));

        addCriterionHandler(QueryModel.IsEmpty.class, (ctx, isEmpty) -> {
            appendEmptyExpression(ctx, IS_NULL + OR + StringUtils.SPACE, " = '' ", IS_EMPTY, isEmpty.getProperty());
        });

        addCriterionHandler(QueryModel.IsNotEmpty.class, (ctx, isNotEmpty) -> {
            appendEmptyExpression(ctx, IS_NOT_NULL + AND + StringUtils.SPACE, " <> '' ", IS_NOT_EMPTY, isNotEmpty.getProperty());
        });

        addCriterionHandler(QueryModel.IdEquals.class, (ctx, idEquals) -> {
            StringBuilder whereClause = ctx.whereClause();
            PersistentEntity persistentEntity = ctx.getPersistentEntity();
            if (persistentEntity.hasCompositeIdentity()) {
                for (PersistentProperty prop : persistentEntity.getCompositeIdentity()) {
                    Object value = idEquals.getValue();
                    if (value instanceof QueryParameter) {
                        QueryParameter qp = (QueryParameter) value;
                        appendCriteriaForOperator(
                                whereClause,
                                ctx,
                                asQueryPropertyPath(ctx.getCurrentTableAlias(), prop),
                                new QueryParameter(qp.getName() + "." + prop.getName()),
                                " = "
                        );
                    }
                    whereClause.append(LOGICAL_AND);
                }
                whereClause.setLength(whereClause.length() - LOGICAL_AND.length());
            } else if (persistentEntity.hasIdentity()) {
                appendCriteriaForOperator(whereClause,
                        ctx,
                        ctx.getRequiredProperty(TypeRole.ID, idEquals.getClass()),
                        idEquals.getValue(),
                        " = "
                );
            } else {
                throw new IllegalStateException("No ID found for entity: " + persistentEntity.getName());
            }
        });

        addCriterionHandler(QueryModel.VersionEquals.class, (ctx, criterion) -> {
            PersistentProperty prop = ctx.getPersistentEntity().getVersion();
            if (prop == null) {
                throw new IllegalStateException("No Version found for entity: " + ctx.getPersistentEntity().getName());
            }
            appendCriteriaForOperator(
                    ctx.whereClause(),
                    ctx,
                    asQueryPropertyPath(ctx.getCurrentTableAlias(), prop),
                    criterion.getValue(),
                    " = "
            );
        });

        addCriterionHandler(QueryModel.GreaterThan.class, valueComparison(GREATER_THAN));
        addCriterionHandler(QueryModel.LessThanEquals.class, valueComparison(LESS_THAN_OR_EQUALS));
        addCriterionHandler(QueryModel.GreaterThanEquals.class, valueComparison(GREATER_THAN_OR_EQUALS));
        addCriterionHandler(QueryModel.LessThan.class, valueComparison(LESS_THAN));
        addCriterionHandler(QueryModel.Like.class, valueComparison(" like "));
        addCriterionHandler(QueryModel.ILike.class, caseInsensitiveValueComparison(" like "));

        addCriterionHandler(QueryModel.Between.class, (ctx, between) -> {
            QueryPropertyPath prop = ctx.getRequiredProperty(between);
            String fromParam = ctx.addParameter(prop.getProperty(), prop.getPath(), between.getFrom());
            String toParam = ctx.addParameter(prop.getProperty(), prop.getPath(), between.getTo());

            StringBuilder whereClause = ctx.whereClause();
            whereClause.append(OPEN_BRACKET);
            appendPropertyRef(whereClause, prop);
            whereClause.append(GREATER_THAN_OR_EQUALS).append(fromParam).append(LOGICAL_AND);
            appendPropertyRef(whereClause, prop);
            whereClause.append(LESS_THAN_OR_EQUALS).append(toParam).append(CLOSE_BRACKET);
        });

        addCriterionHandler(QueryModel.StartsWith.class, valueComparison(this::formatStartsWithBeginning, this::formatEndsWith));
        addCriterionHandler(QueryModel.Contains.class, valueComparison(this::formatStartsWith, this::formatEndsWith));
        addCriterionHandler(QueryModel.EndsWith.class, valueComparison(this::formatStartsWith, this::formEndsWithEnd));

        addCriterionHandler(QueryModel.In.class, (ctx, inQuery) -> {
            QueryPropertyPath propertyPath = ctx.getRequiredProperty(inQuery.getProperty(), QueryModel.In.class);
            String placeholder = ctx.addParameter(propertyPath.getProperty(), propertyPath.getPath(), (QueryParameter) inQuery.getValue());
            StringBuilder whereClause = ctx.whereClause();
            appendPropertyRef(whereClause, propertyPath);
            encodeInExpression(whereClause, placeholder);
        });

        addCriterionHandler(QueryModel.NotIn.class, (ctx, notIn) -> handleSubQuery(ctx, notIn,  " NOT IN ("));
    }

    private <T extends QueryModel.PropertyCriterion> CriterionHandler<T> valueComparison(Supplier<String> prefix, Supplier<String>  suffix) {
        return (ctx, propertyCriterion) -> {
            QueryPropertyPath propertyPath = ctx.getRequiredProperty(propertyCriterion);
            String placeholder = ctx.addParameter(propertyPath.getProperty(), propertyPath.getPath(), (QueryParameter) propertyCriterion.getValue());
            appendPropertyRef(ctx.whereClause(), propertyPath);
            ctx.whereClause().append(prefix.get()).append(placeholder).append(suffix.get());
        };
    }

    private <T extends QueryModel.PropertyCriterion> CriterionHandler<T> valueComparison(String op) {
        return (ctx, propertyCriterion) -> {
            QueryPropertyPath prop = ctx.getRequiredProperty(propertyCriterion);
            appendCriteriaForOperator(ctx.whereClause(), ctx, prop, propertyCriterion.getValue(), op);
        };
    }

    private <T extends QueryModel.PropertyCriterion> CriterionHandler<T> optionalCaseValueComparison(String op) {
        return (ctx, propertyCriterion) -> {
            if (propertyCriterion.isIgnoreCase()) {
                appendCaseInsensitiveCriterion(ctx, propertyCriterion, op);
            } else {
                valueComparison(op).handle(ctx, propertyCriterion);
            }
        };
    }

    private <T extends QueryModel.PropertyCriterion> CriterionHandler<T> caseInsensitiveValueComparison(String op) {
        return (ctx, propertyCriterion) -> appendCaseInsensitiveCriterion(ctx, propertyCriterion, op);
    }

    private <T extends QueryModel.PropertyComparisonCriterion> CriterionHandler<T> comparison(String operator) {
        return (ctx, comparisonCriterion) -> appendPropertyComparison(ctx, comparisonCriterion, operator);
    }

    private <T extends QueryModel.PropertyNameCriterion> CriterionHandler<T> expression(String expression) {
        return (ctx, expressionCriterion) -> {
            appendPropertyRef(ctx.whereClause(), ctx.getRequiredProperty(expressionCriterion));
            ctx.whereClause().append(expression);
        };
    }

    private QueryPropertyPath asQueryPropertyPath(String tableAlias, PersistentProperty persistentProperty) {
        return new QueryPropertyPath(asPersistentPropertyPath(persistentProperty), tableAlias);
    }

    private PersistentPropertyPath asPersistentPropertyPath(PersistentProperty persistentProperty) {
        return new PersistentPropertyPath(Collections.emptyList(), persistentProperty, persistentProperty.getName());
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

    private void appendEmptyExpression(CriteriaContext ctx, String charSequencePrefix, String charSequenceSuffix, String listSuffix, String name) {
        QueryPropertyPath propertyPath = ctx.getRequiredProperty(name, QueryModel.IsEmpty.class);
        StringBuilder whereClause = ctx.whereClause();
        if (propertyPath.getProperty().isAssignable(CharSequence.class)) {
            appendPropertyRef(whereClause, propertyPath);
            whereClause.append(charSequencePrefix);
            appendPropertyRef(whereClause, propertyPath);
            whereClause.append(charSequenceSuffix);
        } else {
            appendPropertyRef(whereClause, propertyPath);
            whereClause.append(listSuffix);
        }
    }

    /**
     * Placeholders for IN queries in SQL require special treatment. This is handled at runtime by some wrapper implementations like JPAQL.
     * But for raw queries the placeholder needs to be expanded to factor in the size of the list or array.
     *
     * @param whereClause The where clause
     * @param placeholder The placeholder
     */
    private void encodeInExpression(StringBuilder whereClause, String placeholder) {
        whereClause
                .append(" IN (")
                .append(placeholder)
                .append(CLOSE_BRACKET);
    }

    @Override
    public QueryResult buildQuery(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("query", query);
        QueryState queryState = newQueryState(query, true, true);

        Collection<JoinPath> joinPaths = query.getJoinPaths();
        for (JoinPath joinPath : joinPaths) {
            queryState.applyJoin(joinPath);
        }

        StringBuilder select = new StringBuilder(SELECT_CLAUSE);
        buildSelectClause(query, queryState, select);
        appendForUpdate(QueryPosition.AFTER_TABLE_NAME, query, select);
        queryState.getQuery().insert(0, select.toString());

        QueryModel.Junction criteria = query.getCriteria();

        if (!criteria.isEmpty() || annotationMetadata.hasStereotype(WhereSpecifications.class) || queryState.getEntity().getAnnotationMetadata().hasStereotype(WhereSpecifications.class)) {
            buildWhereClause(annotationMetadata, criteria, queryState);
        }

        appendOrder(query, queryState);
        appendForUpdate(QueryPosition.END_OF_QUERY, query, queryState.getQuery());

        return QueryResult.of(
                queryState.getQuery().toString(),
                queryState.getParameterBindings(),
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
     * @param queryState  The query state
     * @param queryBuffer
     */
    protected abstract void selectAllColumns(QueryState queryState, StringBuilder queryBuffer);

    /**
     * Selects all columns for the given entity and alias.
     *
     * @param entity      The entity
     * @param alias       The alias
     * @param queryBuffer The buffer to append the columns
     */
    protected abstract void selectAllColumns(PersistentEntity entity, String alias, StringBuilder queryBuffer);

    /**
     * Begins the query state.
     *
     * @param query      The query
     * @param allowJoins Whether joins are allowed
     * @param useAlias   Whether alias shoudl be used
     * @return The query state object
     */
    private QueryState newQueryState(@NonNull QueryModel query, boolean allowJoins, boolean useAlias) {
        return new QueryState(query, allowJoins, useAlias);
    }

    private void buildSelectClause(QueryModel query, QueryState queryState, StringBuilder queryString) {
        String logicalName = queryState.getRootAlias();
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
     *
     * @param entity The entity
     * @return True if they should be escaped
     */
    protected boolean shouldEscape(@NonNull PersistentEntity entity) {
        return entity.getAnnotationMetadata().booleanValue(MappedEntity.class, "escape").orElse(true);
    }

    /**
     * Get the AS keyword to use for table aliases.
     *
     * @return The AS keyword if any
     */
    protected String getTableAsKeyword() {
        return AS_CLAUSE;
    }

    /**
     * Quote a column name for the dialect.
     *
     * @param persistedName The persisted name.
     * @return The quoted name
     */
    protected String quote(String persistedName) {
        return "\"" + persistedName + "\"";
    }

    private void buildSelect(QueryState queryState, StringBuilder queryString, List<QueryModel.Projection> projectionList, String tableAlias, PersistentEntity entity) {
        if (projectionList.isEmpty()) {
            selectAllColumns(queryState, queryString);
        } else {
            for (Iterator i = projectionList.iterator(); i.hasNext(); ) {
                QueryModel.Projection projection = (QueryModel.Projection) i.next();
                if (projection instanceof QueryModel.CountProjection) {
                    appendProjectionRowCount(queryString, tableAlias);
                } else if (projection instanceof QueryModel.DistinctProjection) {
                    queryString.append("DISTINCT(")
                            .append(tableAlias)
                            .append(CLOSE_BRACKET);
                } else if (projection instanceof QueryModel.IdProjection) {
                    if (entity.hasCompositeIdentity()) {
                        for (PersistentProperty identity : entity.getCompositeIdentity()) {
                            appendPropertyProjection(queryString, asQueryPropertyPath(queryState.getRootAlias(), identity));
                            queryString.append(COMMA);
                        }
                        queryString.setLength(queryString.length() - 1);
                    } else if (entity.hasIdentity()) {
                        PersistentProperty identity = entity.getIdentity();
                        if (identity == null) {
                            throw new IllegalArgumentException("Cannot query on ID with entity that has no ID");
                        }
                        appendPropertyProjection(queryString, asQueryPropertyPath(queryState.getRootAlias(), identity));
                    } else {
                        throw new IllegalArgumentException("Cannot query on ID with entity that has no ID");
                    }
                } else if (projection instanceof QueryModel.PropertyProjection) {
                    QueryModel.PropertyProjection pp = (QueryModel.PropertyProjection) projection;
                    String alias = pp.getAlias().orElse(null);
                    if (projection instanceof QueryModel.AvgProjection) {
                        appendFunctionProjection(queryState.getEntity(), AVG, pp, tableAlias, queryString);
                    } else if (projection instanceof QueryModel.DistinctPropertyProjection) {
                        appendFunctionProjection(queryState.getEntity(), DISTINCT, pp, tableAlias, queryString);
                    } else if (projection instanceof QueryModel.SumProjection) {
                        appendFunctionProjection(queryState.getEntity(), SUM, pp, tableAlias, queryString);
                    } else if (projection instanceof QueryModel.MinProjection) {
                        appendFunctionProjection(queryState.getEntity(), MIN, pp, tableAlias, queryString);
                    } else if (projection instanceof QueryModel.MaxProjection) {
                        appendFunctionProjection(queryState.getEntity(), MAX, pp, tableAlias, queryString);
                    } else if (projection instanceof QueryModel.CountDistinctProjection) {
                        appendFunctionProjection(queryState.getEntity(), COUNT_DISTINCT, pp, tableAlias, queryString);
                        queryString.append(CLOSE_BRACKET);
                    } else {
                        String propertyName = pp.getPropertyName();
                        PersistentPropertyPath propertyPath = entity.getPropertyPath(propertyName);
                        if (propertyPath == null) {
                            throw new IllegalArgumentException("Cannot project on non-existent property: " + propertyName);
                        }
                        PersistentProperty property = propertyPath.getProperty();
                        if (property instanceof Association && !(property instanceof Embedded)) {
                            String joinAlias = queryState.computeAlias(propertyPath.getPath());
                            selectAllColumns(((Association) property).getAssociatedEntity(), joinAlias, queryString);
                        } else {
                            appendPropertyProjection(queryString, findProperty(queryState, propertyName, null));
                        }
                    }
                    if (alias != null) {
                        queryString.append(AS_CLAUSE).append(alias);
                    }
                }

                if (i.hasNext()) {
                    queryString.append(COMMA);
                }
            }
        }
    }

    private void appendPropertyProjection(StringBuilder sb, QueryPropertyPath propertyPath) {
        if (!computePropertyPaths()) {
            sb.append(propertyPath.getTableAlias()).append(DOT).append(propertyPath.getPath());
            return;
        }
        String tableAlias = propertyPath.getTableAlias();
        boolean escape = propertyPath.shouldEscape();
        NamingStrategy namingStrategy = propertyPath.getNamingStrategy();
        int length = sb.length();
        traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), (associations, property) -> {
            String columnName = namingStrategy.mappedName(associations, property);
            if (escape) {
                columnName = quote(columnName);
            }
            sb.append(tableAlias).append(DOT).append(columnName).append(COMMA);
        });
        int newLength = sb.length();
        if (length != newLength) {
            sb.setLength(newLength - 1);
        }
    }

    private void appendFunctionProjection(
            PersistentEntity entity,
            String functionName,
            QueryModel.PropertyProjection propertyProjection,
            String tableAlias,
            StringBuilder queryString) {
        PersistentPropertyPath propertyPath = entity.getPropertyPath(propertyProjection.getPropertyName());
        if (propertyPath == null) {
            throw new IllegalArgumentException("Cannot project on non-existent property: " + propertyProjection.getPropertyName());
        }
        String columnName;
        if (computePropertyPaths()) {
            columnName = entity.getNamingStrategy().mappedName(propertyPath.getAssociations(), propertyPath.getProperty());
            if (shouldEscape(entity)) {
                columnName = quote(columnName);
            }
        } else {
            columnName = propertyPath.getPath();
        }
        queryString.append(functionName)
                .append(OPEN_BRACKET)
                .append(tableAlias)
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

    private void handleAssociationCriteria(CriteriaContext ctx, AssociationQuery associationQuery) {
        QueryState queryState = ctx.getQueryState();
        Association association = associationQuery.getAssociation();
        if (association == null) {
            return;
        }

        // Join should be applied in `findProperty` only in cases when it's needed; we don't need to join to access the identity

        String associationPath = associationQuery.getPath();

        CriteriaContext associatedContext = new CriteriaContext() {

            @Override
            public String getCurrentTableAlias() {
                return ctx.getCurrentTableAlias();
            }

            @Override
            public QueryState getQueryState() {
                return ctx.getQueryState();
            }

            @Override
            public PersistentEntity getPersistentEntity() {
                return ctx.getPersistentEntity();
            }

            @Override
            public QueryPropertyPath getRequiredProperty(String name, Class<?> criterionClazz) {
                if (StringUtils.isNotEmpty(associationPath)) {
                    name = associationPath + DOT + name;
                }
                return findPropertyInternal(queryState, getPersistentEntity(), getCurrentTableAlias(), name, criterionClazz);
            }
        };
        handleJunction(associatedContext, associationQuery.getCriteria());
    }

    private void buildWhereClause(AnnotationMetadata annotationMetadata, QueryModel.Junction criteria, QueryState queryState) {
        StringBuilder whereClause = queryState.getWhereClause();
        StringBuilder queryClause = queryState.getQuery();
        if (!criteria.isEmpty()) {
            whereClause.append(WHERE_CLAUSE);
            if (criteria instanceof QueryModel.Negation) {
                whereClause.append(NOT_CLAUSE);
            }

            CriteriaContext ctx = new CriteriaContext() {

                @Override
                public String getCurrentTableAlias() {
                    return queryState.getRootAlias();
                }

                @Override
                public QueryState getQueryState() {
                    return queryState;
                }

                @Override
                public PersistentEntity getPersistentEntity() {
                    return queryState.getEntity();
                }

                @Override
                public QueryPropertyPath getRequiredProperty(String name, Class<?> criterionClazz) {
                    return findProperty(queryState, name, criterionClazz);
                }

            };

            whereClause.append(OPEN_BRACKET);
            handleJunction(ctx, criteria);

            String whereStr = whereClause.toString();
            String additionalWhere = buildAdditionalWhereString(queryState.getRootAlias(), queryState.getEntity(), annotationMetadata);
            if (StringUtils.isNotEmpty(additionalWhere)) {
                StringBuffer additionalWhereBuilder = new StringBuffer();
                Matcher matcher = QueryBuilder.VARIABLE_PATTERN.matcher(additionalWhere);
                while (matcher.find()) {
                    String name = matcher.group(3);
                    String placeholder = queryState.addAdditionalRequiredParameter(name);
                    matcher.appendReplacement(additionalWhereBuilder, placeholder);
                }
                matcher.appendTail(additionalWhereBuilder);
                additionalWhere = additionalWhereBuilder.toString();
            }
            if (whereStr.equals(WHERE_CLAUSE + OPEN_BRACKET)) {
                if (StringUtils.isNotEmpty(additionalWhere)) {
                    queryClause.append(whereStr).append(additionalWhere).append(CLOSE_BRACKET);
                }
            } else {
                queryClause.append(whereStr);
                if (StringUtils.isNotEmpty(additionalWhere)) {
                    queryClause.append(LOGICAL_AND).append(OPEN_BRACKET).append(additionalWhere).append(CLOSE_BRACKET);
                }
                queryClause.append(CLOSE_BRACKET);
            }
        } else {
            final String additionalWhereString = buildAdditionalWhereString(queryState.getRootAlias(), queryState.getEntity(), annotationMetadata);
            if (StringUtils.isNotEmpty(additionalWhereString)) {
                whereClause.append(WHERE_CLAUSE)
                        .append(OPEN_BRACKET)
                        .append(additionalWhereString)
                        .append(CLOSE_BRACKET);
                queryClause.append(whereClause.toString());
            }
        }
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
                QueryPropertyPath propertyPath = findProperty(queryState, order.getProperty(), Sort.Order.class);
                String currentAlias = propertyPath.getTableAlias();
                if (currentAlias != null) {
                    buff.append(currentAlias).append(DOT);
                }
                if (computePropertyPaths()) {
                    buff.append(propertyPath.getColumnName()).append(SPACE).append(order.getDirection().toString());
                } else {
                    buff.append(propertyPath.getPath()).append(SPACE).append(order.getDirection().toString());
                }
                if (i.hasNext()) {
                    buff.append(",");
                }
            }
        }
    }

    protected void appendForUpdate(QueryPosition queryPosition, QueryModel query, StringBuilder queryBuilder) {
    }

    private void handleJunction(CriteriaContext ctx, QueryModel.Junction criteria) {
        StringBuilder whereClause = ctx.whereClause();
        int length = whereClause.length();
        final String operator = criteria instanceof QueryModel.Conjunction ? LOGICAL_AND : LOGICAL_OR;
        for (QueryModel.Criterion criterion : criteria.getCriteria()) {
            CriterionHandler<QueryModel.Criterion> criterionHandler = queryHandlers.get(criterion.getClass());
            if (criterionHandler == null) {
                throw new IllegalArgumentException("Queries of type " + criterion.getClass().getSimpleName() + " are not supported by this implementation");
            }
            int beforeHandleLength = whereClause.length();
            criterionHandler.handle(ctx, criterion);
            if (beforeHandleLength != whereClause.length()) {
                whereClause.append(operator);
            }
        }
        int newLength = whereClause.length();
        if (newLength != length) {
            whereClause.setLength(newLength - operator.length());
        }
    }

    private void appendCriteriaForOperator(StringBuilder whereClause,
                                           PropertyParameterCreator propertyParameterCreator,
                                           QueryPropertyPath propertyPath,
                                           Object value,
                                           String operator) {

        if (value instanceof QueryParameter) {
            QueryParameter queryParameter = (QueryParameter) value;
            boolean computePropertyPaths = computePropertyPaths();
            if (!computePropertyPaths) {
                String placeholder = propertyParameterCreator.addParameter(propertyPath.getProperty(), propertyPath.getPath(), queryParameter);
                appendPropertyRef(whereClause, propertyPath);
                whereClause.append(operator).append(placeholder);
                return;
            }

            String currentAlias = propertyPath.getTableAlias();
            NamingStrategy namingStrategy = propertyPath.getNamingStrategy();
            boolean shouldEscape = propertyPath.shouldEscape();

            int length = whereClause.length();
            String rootPath = asPath(propertyPath.getAssociations(), propertyPath.getProperty());

            traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), (associations, property) -> {
                String readTransformer = getDataTransformerReadValue(currentAlias, property).orElse(null);
                if (readTransformer != null) {
                    whereClause.append(readTransformer);
                } else {
                    if (currentAlias != null) {
                        whereClause.append(currentAlias).append(DOT);
                    }
                    String columnName = namingStrategy.mappedName(associations, property);
                    if (shouldEscape) {
                        columnName = quote(columnName);
                    }
                    whereClause.append(columnName);
                }

                String path = asPath(associations, property);
                if (path.startsWith(rootPath)) {
                    path = queryParameter.getName() + path.substring(rootPath.length());
                }

                String placeholder = propertyParameterCreator.addParameter(property, asPath(associations, property), new QueryParameter(path));
                whereClause.append(operator).append(placeholder).append(LOGICAL_AND);
            });

            int newLength = whereClause.length();
            if (newLength != length) {
                whereClause.setLength(newLength - LOGICAL_AND.length());
            }

        } else {
            throw new IllegalStateException("Unknown value: " + value);
        }
    }

    private void appendPropertyRef(StringBuilder sb, QueryPropertyPath propertyPath) {
        String tableAlias = propertyPath.getTableAlias();
        String readTransformer = getDataTransformerReadValue(tableAlias, propertyPath.getProperty()).orElse(null);
        if (readTransformer != null) {
            sb.append(readTransformer);
            return;
        }
        if (tableAlias != null) {
            sb.append(tableAlias).append(DOT);
        }
        boolean computePropertyPaths = computePropertyPaths();
        if (computePropertyPaths) {
            sb.append(propertyPath.getColumnName());
        } else {
            sb.append(propertyPath.getPath());
        }
    }

    private void appendCaseInsensitiveCriterion(CriteriaContext ctx,
                                                QueryModel.PropertyCriterion criterion,
                                                String operator) {
        QueryPropertyPath propertyPath = ctx.getRequiredProperty(criterion);
        String placeholder = ctx.addParameter(propertyPath.getProperty(), propertyPath.getPath(), (QueryParameter) criterion.getValue());
        StringBuilder whereClause = ctx.whereClause();
        whereClause.append("lower(");
        appendPropertyRef(whereClause, propertyPath);
        whereClause.append(")")
                .append(operator)
                .append("lower(")
                .append(placeholder)
                .append(")");
    }

    /**
     * For handling subqueries.
     *
     * @param ctx                  The criteria context
     * @param subqueryCriterion    The subquery criterion
     * @param comparisonExpression The comparison expression
     */
    protected void handleSubQuery(CriteriaContext ctx, QueryModel.SubqueryCriterion subqueryCriterion, String comparisonExpression) {
        QueryPropertyPath propertyPath = ctx.getRequiredProperty(subqueryCriterion.getProperty(), QueryModel.In.class);
        StringBuilder whereClause = ctx.whereClause();
        appendPropertyRef(whereClause, propertyPath);
        whereClause.append(comparisonExpression);
        // TODO: support subqueryCriterion
        whereClause.append(CLOSE_BRACKET);
    }

    private void buildUpdateStatement(QueryState queryState, List<String> propertiesToUpdate) {
        StringBuilder queryString = queryState.getQuery();
        queryString.append(SPACE).append("SET").append(SPACE);

        // keys need to be sorted before query is built

        List<QueryPropertyPath> properties = propertiesToUpdate.stream()
                .map(property -> {
                    QueryPropertyPath propertyPath = findProperty(queryState, property, null);
                    if (propertyPath.getProperty() instanceof Association && ((Association) propertyPath.getProperty()).isForeignKey()) {
                        throw new IllegalArgumentException("Foreign key associations cannot be updated as part of a batch update statement");
                    }
                    return propertyPath;
                })
                .filter(propertyPath -> !propertyPath.getProperty().isGenerated())
                .collect(Collectors.toList());

        int length = queryString.length();
        if (!computePropertyPaths()) {
            for (QueryPropertyPath propertyPath : properties) {
                PersistentProperty prop = propertyPath.getProperty();
                String placeholder = queryState.addParameter(prop, propertyPath.getPath());
                String tableAlias = propertyPath.getTableAlias();
                if (tableAlias != null) {
                    queryString.append(tableAlias).append(DOT);
                }
                queryString.append(propertyPath.getPath()).append('=');
                appendUpdateSetParameter(queryString, tableAlias, prop, placeholder);
                queryString.append(COMMA);
            }
        } else {
            NamingStrategy namingStrategy = queryState.getEntity().getNamingStrategy();
            for (QueryPropertyPath propertyPath : properties) {
                traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), (associations, property) -> {
                    String placeholder = queryState.addParameter(property, asPath(associations, property));
                    String tableAlias = propertyPath.getTableAlias();
                    if (tableAlias != null) {
                        queryString.append(tableAlias).append(DOT);
                    }
                    String columnName = namingStrategy.mappedName(associations, property);
                    if (queryState.escape) {
                        columnName = quote(columnName);
                    }
                    queryString.append(columnName).append('=');
                    appendUpdateSetParameter(queryString, tableAlias, property, placeholder);
                    queryString.append(COMMA);
                });
            }
        }
        int newLength = queryString.length();
        if (length != newLength) {
            queryString.setLength(newLength - 1);
        }
    }

    /**
     * Should embedded queries by expanded by the implementation.
     *
     * @return True if they should
     */
    protected boolean isExpandEmbedded() {
        return false;
    }

    /**
     * Appends the SET=? call to the query string.
     *
     * @param sb            The string builder
     * @param alias         The alias
     * @param prop          The property
     * @param placeholder   The parameter
     */
    protected void appendUpdateSetParameter(StringBuilder sb, String alias, PersistentProperty prop, String placeholder) {
        sb.append(getDataTransformerWriteValue(alias, prop).orElse(placeholder));
    }

    private void appendPropertyComparison(CriteriaContext ctx, QueryModel.PropertyComparisonCriterion comparisonCriterion, String operator) {
        StringBuilder sb = ctx.whereClause();
        appendPropertyRef(sb, ctx.getRequiredProperty(comparisonCriterion.getProperty(), comparisonCriterion.getClass()));
        sb.append(operator);
        appendPropertyRef(sb, ctx.getRequiredProperty(comparisonCriterion.getOtherProperty(), comparisonCriterion.getClass()));
    }

    @NonNull
    private QueryPropertyPath findProperty(QueryState queryState, String name, Class criterionType) {
        return findPropertyInternal(queryState, queryState.getEntity(), queryState.getRootAlias(), name, criterionType);
    }

    private QueryPropertyPath findPropertyInternal(QueryState queryState, PersistentEntity entity, String tableAlias, String name, Class criterionType) {
        PersistentPropertyPath propertyPath = entity.getPropertyPath(name);
        if (propertyPath != null) {
            if (propertyPath.getAssociations().isEmpty()) {
                return new QueryPropertyPath(propertyPath, tableAlias);
            }
            Association joinAssociation = null;
            StringJoiner joinPathJoiner = new StringJoiner(".");
            for (Association association : propertyPath.getAssociations()) {
                joinPathJoiner.add(association.getName());
                if (association instanceof Embedded) {
                    continue;
                }
                if (joinAssociation == null) {
                    joinAssociation = association;
                    continue;
                }
                // We don't need to join to access the id of the relation
                if (association != joinAssociation.getAssociatedEntity().getIdentity()) {
                    if (!queryState.isAllowJoins()) {
                        throw new IllegalArgumentException("Joins cannot be used in a DELETE or UPDATE operation");
                    }
                    String joinStringPath = joinPathJoiner.toString();
                    String joinAlias = joinInPath(queryState, joinStringPath);
                    // Continue to look for a joined property
                    String joinedPropertyName = name.replaceFirst(Pattern.quote(joinStringPath + DOT), "");
                    return findPropertyInternal(queryState, joinAssociation.getAssociatedEntity(), joinAlias, joinedPropertyName, criterionType);
                }
                joinAssociation = null;
            }
            // We don't need to join to access the id of the relation
            PersistentProperty property = propertyPath.getProperty();
            if (joinAssociation != null && property != joinAssociation.getAssociatedEntity().getIdentity()) {
                String joinAlias = joinInPath(queryState, joinPathJoiner.toString());
                // 'joinPath.prop' should be represented as a path of 'prop' with a join alias
                return new QueryPropertyPath(
                        new PersistentPropertyPath(Collections.emptyList(), property, property.getName()),
                        joinAlias
                );
            }
        } else if (TypeRole.ID.equals(name) && entity.getIdentity() != null) {
            // special case handling for ID
            return new QueryPropertyPath(
                    new PersistentPropertyPath(Collections.emptyList(), entity.getIdentity(), entity.getIdentity().getName()),
                    queryState.getRootAlias()
            );
        }
        if (propertyPath == null) {
            if (criterionType == null || criterionType == Sort.Order.class) {
                throw new IllegalArgumentException("Cannot order on non-existent property path: " + name);
            } else {
                throw new IllegalArgumentException("Cannot use [" + criterionType.getSimpleName() + "] criterion on non-existent property path: " + name);
            }
        }
        return new QueryPropertyPath(propertyPath, tableAlias);
    }

    private String joinInPath(QueryState queryState, String joinStringPath) {
        QueryModel queryModel = queryState.getQueryModel();
        JoinPath joinPath = queryModel.getJoinPath(joinStringPath).orElse(null);
        if (joinPath == null) {
            joinPath = queryModel.join(joinStringPath, Join.Type.DEFAULT, null);
        }
        if (queryState.isAllowJoins()) {
            return queryState.applyJoin(joinPath);
        } else {
            throw new IllegalArgumentException("Joins are not allowed for batch update queries");
        }
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
        QueryState queryState = newQueryState(query, false, isAliasForBatch());
        StringBuilder queryString = queryState.getQuery();
        String tableAlias = queryState.getRootAlias();
        String tableName = getTableName(entity);
        queryString.append(UPDATE_CLAUSE).append(tableName);
        if (tableAlias != null) {
            queryString.append(SPACE).append(tableAlias);
        }
        buildUpdateStatement(queryState, propertiesToUpdate);
        buildWhereClause(annotationMetadata, query.getCriteria(), queryState);
        return QueryResult.of(
                queryState.getQuery().toString(),
                queryState.getParameterBindings(),
                queryState.getAdditionalRequiredParameters()
        );
    }

    @Override
    public QueryResult buildDelete(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query) {
        PersistentEntity entity = query.getPersistentEntity();
        QueryState queryState = newQueryState(query, false, isAliasForBatch());
        StringBuilder queryString = queryState.getQuery();
        String tableAlias = queryState.getRootAlias();
        StringBuilder buffer = appendDeleteClause(queryString);
        String tableName = getTableName(entity);
        buffer.append(tableName).append(SPACE);
        if (tableAlias != null) {
            buffer.append(getTableAsKeyword()).append(tableAlias);
        }
        buildWhereClause(annotationMetadata, query.getCriteria(), queryState);
        return QueryResult.of(
                queryString.toString(),
                queryState.getParameterBindings(),
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
                                            new Association[]{association},
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
                Collections.emptyList(),
                Collections.emptyMap()
        );
    }

    /**
     * Join associations and property as path.
     *
     * @param associations The associations
     * @param property     The property
     * @return joined path
     */
    protected String asPath(List<Association> associations, PersistentProperty property) {
        if (associations.isEmpty()) {
            return property.getName();
        }
        StringJoiner joiner = new StringJoiner(".");
        for (Association association : associations) {
            joiner.add(association.getName());
        }
        joiner.add(property.getName());
        return joiner.toString();
    }

    /**
     * Traverses properties that should be persisted.
     *
     * @param property The property to start traversing from
     * @param consumer The function to invoke on every property
     */
    protected void traversePersistentProperties(PersistentProperty property, BiConsumer<List<Association>, PersistentProperty> consumer) {
        traversePersistentProperties(Collections.emptyList(), property, consumer);
    }

    /**
     * Traverses properties that should be persisted.
     *
     * @param persistentEntity The persistent entity
     * @param consumer         The function to invoke on every property
     */
    protected void traversePersistentProperties(PersistentEntity persistentEntity, BiConsumer<List<Association>, PersistentProperty> consumer) {
        if (persistentEntity.getIdentity() != null) {
            traversePersistentProperties(Collections.emptyList(), persistentEntity.getIdentity(), consumer);
        }
        if (persistentEntity.getVersion() != null) {
            traversePersistentProperties(Collections.emptyList(), persistentEntity.getVersion(), consumer);
        }
        for (PersistentProperty property : persistentEntity.getPersistentProperties()) {
            traversePersistentProperties(Collections.emptyList(), property, consumer);
        }
    }

    /**
     * Traverses properties that should be persisted.
     *
     * @param persistentEntity The persistent entity
     * @param includeIdentity  Should be identifier included
     * @param includeVersion   Should be version included
     * @param consumer         The function to invoke on every property
     */
    protected void traversePersistentProperties(PersistentEntity persistentEntity, boolean includeIdentity, boolean includeVersion, BiConsumer<List<Association>, PersistentProperty> consumer) {
        if (includeIdentity && persistentEntity.getIdentity() != null) {
            traversePersistentProperties(Collections.emptyList(), persistentEntity.getIdentity(), consumer);
        }
        if (includeVersion && persistentEntity.getVersion() != null) {
            traversePersistentProperties(Collections.emptyList(), persistentEntity.getVersion(), consumer);
        }
        for (PersistentProperty property : persistentEntity.getPersistentProperties()) {
            traversePersistentProperties(Collections.emptyList(), property, consumer);
        }
    }

    private void traversePersistentProperties(List<Association> associations,
                                              PersistentProperty property,
                                              BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        if (property instanceof Embedded) {
            Embedded embedded = (Embedded) property;
            PersistentEntity embeddedEntity = embedded.getAssociatedEntity();
            Collection<? extends PersistentProperty> embeddedProperties = embeddedEntity.getPersistentProperties();
            List<Association> newAssociations = new ArrayList<>(associations);
            newAssociations.add((Association) property);
            for (PersistentProperty embeddedProperty : embeddedProperties) {
                traversePersistentProperties(newAssociations, embeddedProperty, consumerProperty);
            }
        } else if (property instanceof Association) {
            Association association = (Association) property;
            if (association.isForeignKey()) {
                return;
            }
            List<Association> newAssociations = new ArrayList<>(associations);
            newAssociations.add((Association) property);
            PersistentEntity associatedEntity = association.getAssociatedEntity();
            PersistentProperty assocIdentity = associatedEntity.getIdentity();
            if (assocIdentity == null) {
                throw new IllegalStateException("Identity cannot be missing for: " + associatedEntity);
            }
            if (assocIdentity instanceof Association) {
                traversePersistentProperties(newAssociations, assocIdentity, consumerProperty);
            } else {
                consumerProperty.accept(newAssociations, assocIdentity);
            }
        } else {
            consumerProperty.accept(associations, property);
        }
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
     *
     * @param alias query table alias
     * @param prop  a property
     * @return optional transformed value
     */
    protected Optional<String> getDataTransformerReadValue(String alias, PersistentProperty prop) {
        return getDataTransformerValue(alias, prop, "read");
    }

    /**
     * Returns transformed value if the data transformer id defined.
     *
     * @param alias query table alias
     * @param prop  a property
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
     * Adds criterion handler.
     *
     * @param clazz The handler class
     * @param handler The handler
     * @param <T> The criterion type
     */
    protected <T extends QueryModel.Criterion> void addCriterionHandler(Class<T> clazz, CriterionHandler<T> handler) {
        queryHandlers.put(clazz, handler);
    }

    /**
     * A criterion handler.
     * @param <T> The criterion type
     */
    protected interface CriterionHandler<T extends QueryModel.Criterion> {

        /**
         * Handles a criterion.
         *
         * @param context   The context
         * @param criterion The criterion
         */
        void handle(CriteriaContext context, T criterion);
    }

    /**
     * A criterion context.
     */
    protected interface CriteriaContext extends PropertyParameterCreator {

        String getCurrentTableAlias();

        QueryState getQueryState();

        PersistentEntity getPersistentEntity();

        QueryPropertyPath getRequiredProperty(String name, Class<?> criterionClazz);

        default String addParameter(@NonNull PersistentProperty persistentProperty, String path, @Nullable QueryParameter value) {
            return getQueryState().addParameter(persistentProperty, path, value);
        }

        default QueryPropertyPath getRequiredProperty(QueryModel.PropertyNameCriterion propertyCriterion) {
            return getRequiredProperty(propertyCriterion.getProperty(), propertyCriterion.getClass());
        }

        default StringBuilder whereClause() {
            return getQueryState().getWhereClause();
        }

    }

    /**
     * The state of the query.
     */
    @Internal
    protected final class QueryState implements PropertyParameterCreator {
        private final String rootAlias;
        private final Map<String, String> appliedJoinPaths = new HashMap<>();
        private final AtomicInteger position = new AtomicInteger(0);
        private final Map<String, String> additionalRequiredParameters = new LinkedHashMap<>();
        private final List<QueryParameterBinding> parameterBindings;
        private final StringBuilder query = new StringBuilder();
        private final StringBuilder whereClause = new StringBuilder();
        private final boolean allowJoins;
        private final QueryModel queryObject;
        private final boolean escape;
        private final PersistentEntity entity;

        private QueryState(QueryModel query, boolean allowJoins, boolean useAlias) {
            this.allowJoins = allowJoins;
            this.queryObject = query;
            this.entity = query.getPersistentEntity();
            this.escape = AbstractSqlLikeQueryBuilder.this.shouldEscape(entity);
            this.rootAlias = useAlias ? getAliasName(entity) : null;
            this.parameterBindings = new ArrayList<>(entity.getPersistentPropertyNames().size());
        }

        /**
         * @return The root alias
         */
        public @Nullable
        String getRootAlias() {
            return rootAlias;
        }

        /**
         * @return The entity
         */
        public PersistentEntity getEntity() {
            return entity;
        }

        /**
         * Add a required parameter.
         *
         * @param name The name
         */
        public String addAdditionalRequiredParameter(@NonNull String name) {
            Placeholder placeholder = newParameter();
            additionalRequiredParameters.put(placeholder.key, name);
            return placeholder.name;
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
        private Placeholder newParameter() {
            return formatParameter(position.incrementAndGet());
        }

        /**
         * Applies a join for the given association.
         *
         * @param jp The join path
         * @return The alias
         */
        public String applyJoin(@NonNull JoinPath jp) {
            String joinAlias = appliedJoinPaths.get(jp.getPath());
            if (joinAlias != null) {
                return joinAlias;
            }
            Optional<JoinPath> ojp = getQueryModel().getJoinPath(jp.getPath());
            if (ojp.isPresent()) {
                jp = ojp.get();
            }
            StringBuilder stringBuilder = getQuery();
            Join.Type jt = jp.getJoinType();
            String joinType = resolveJoinType(jt);

            String[] associationAlias = buildJoin(
                    getRootAlias(),
                    jp,
                    joinType,
                    stringBuilder,
                    appliedJoinPaths,
                    this);
            Association[] associationArray = jp.getAssociationPath();
            StringJoiner associationPath = new StringJoiner(".");
            String lastAlias = null;
            for (int i = 0; i < associationAlias.length; i++) {
                associationPath.add(associationArray[i].getName());
                String computedAlias = associationAlias[i];
                appliedJoinPaths.put(associationPath.toString(), computedAlias);
                lastAlias = computedAlias;
            }
            return lastAlias;
        }

        /**
         * Computes the alias for the given association path given the current state of the joins.
         *
         * @param associationPath The assocation path.
         * @return The alias
         */
        public @NonNull
        String computeAlias(String associationPath) {
            if (appliedJoinPaths.containsKey(associationPath)) {
                return appliedJoinPaths.get(associationPath);
            } else {
                int i = associationPath.indexOf('.');
                if (i > -1) {
                    String p = associationPath.substring(0, i);
                    if (appliedJoinPaths.containsKey(p)) {
                        return appliedJoinPaths.get(p) + DOT + associationPath.substring(i + 1);
                    }
                }
            }
            return getRootAlias() + DOT + associationPath;
        }

        /**
         * @return Should escape the query
         */
        public boolean shouldEscape() {
            return escape;
        }

        /**
         * The additional required parameters.
         *
         * @return The parameters
         */
        public @NotNull Map<String, String> getAdditionalRequiredParameters() {
            return this.additionalRequiredParameters;
        }

        /**
         * The parameter binding.
         *
         * @return The parameter binding
         */
        public List<QueryParameterBinding> getParameterBindings() {
            return parameterBindings;
        }

        @Override
        public String addParameter(@NonNull PersistentProperty persistentProperty, String path, @Nullable QueryParameter queryParameter) {
            Placeholder placeholder = newParameter();
            parameterBindings.add(QueryParameterBinding.of(
                    placeholder.key,
                    path,
                    persistentProperty.getDataType(),
                    queryParameter,
                    persistentProperty.findAnnotation(AutoPopulated.class).map(ap -> ap.getRequiredValue(AutoPopulated.UPDATEABLE, Boolean.class)).orElse(false)
            ));
            return placeholder.name;
        }
    }

    private interface PropertyParameterCreator {

        default String addParameter(@NonNull PersistentProperty persistentProperty, @NotNull String path) {
            return addParameter(persistentProperty, path, null);
        }

        String addParameter(@NonNull PersistentProperty persistentProperty, @NotNull String path, @Nullable QueryParameter value);

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
    protected class QueryPropertyPath {
        private final PersistentPropertyPath propertyPath;
        private final String tableAlias;

        /**
         * Default constructor.
         *
         * @param propertyPath The propertyPath
         * @param tableAlias   The tableAlias
         */
        public QueryPropertyPath(@NotNull PersistentPropertyPath propertyPath, @Nullable String tableAlias) {
            this.propertyPath = propertyPath;
            this.tableAlias = tableAlias;
        }

        /**
         * @return The associations
         */
        @NonNull
        public List<Association> getAssociations() {
            return propertyPath.getAssociations();
        }

        /**
         * @return The property
         */
        @NonNull
        public PersistentProperty getProperty() {
            return propertyPath.getProperty();
        }

        /**
         * @return The path
         */
        @NonNull
        public String getPath() {
            return propertyPath.getPath();
        }

        /**
         * @return The path
         */
        @Nullable
        public String getTableAlias() {
            return tableAlias;
        }

        /**
         * @return already escaped column name
         */
        public String getColumnName() {
            String columnName = getNamingStrategy().mappedName(propertyPath.getAssociations(), propertyPath.getProperty());
            if (shouldEscape()) {
                return quote(columnName);
            }
            return columnName;
        }

        /**
         * @return the naming strategy
         */
        public NamingStrategy getNamingStrategy() {
            return propertyPath.getNamingStrategy();
        }

        /**
         * @return should escape
         */
        public boolean shouldEscape() {
            return AbstractSqlLikeQueryBuilder.this.shouldEscape(propertyPath.findPropertyOwner().get());
        }
    }

    protected enum QueryPosition {
        AFTER_TABLE_NAME, END_OF_QUERY
    }
}
