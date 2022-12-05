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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.DataTransformer;
import io.micronaut.data.annotation.IgnoreWhere;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.annotation.repeatable.WhereSpecifications;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.impl.LiteralExpression;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.AssociationQuery;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.builder.sql.Dialect;

import javax.validation.constraints.NotNull;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * An abstract class for builders that build SQL-like queries.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@SuppressWarnings("checkstyle:FileLength")
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
    protected static final String NOT = "NOT";
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
    protected static final String IS_NOT_NULL = " IS NOT NULL";
    protected static final String IS_EMPTY = " IS EMPTY";
    protected static final String IS_NOT_EMPTY = " IS NOT EMPTY";
    protected static final String IS_NULL = " IS NULL";
    protected static final String EQUALS_TRUE = " = TRUE";
    protected static final String EQUALS_FALSE = " = FALSE";
    protected static final String GREATER_THAN_OR_EQUALS = " >= ";
    protected static final String LESS_THAN_OR_EQUALS = " <= ";
    protected static final String LESS_THAN = " < ";
    protected static final String GREATER_THAN = " > ";
    protected static final String EQUALS = " = ";
    protected static final String NOT_EQUALS = " != ";
    protected static final String ALIAS_REPLACE = "@.";
    protected static final String ALIAS_REPLACE_QUOTED = "@\\.";
    protected final Map<Class, CriterionHandler> queryHandlers = new HashMap<>(30);

    {
        addCriterionHandler(AssociationQuery.class, this::handleAssociationCriteria);

        addCriterionHandler(QueryModel.Negation.class, (ctx, negation) -> {
            ctx.query().append(NOT).append(OPEN_BRACKET);
            handleJunction(ctx, negation);
            ctx.query().append(CLOSE_BRACKET);
        });

        addCriterionHandler(QueryModel.Conjunction.class, (ctx, conjunction) -> {
            ctx.query().append(OPEN_BRACKET);
            handleJunction(ctx, conjunction);
            ctx.query().append(CLOSE_BRACKET);
        });

        addCriterionHandler(QueryModel.Disjunction.class, (ctx, disjunction) -> {
            ctx.query().append(OPEN_BRACKET);
            handleJunction(ctx, disjunction);
            ctx.query().append(CLOSE_BRACKET);
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
            appendEmptyExpression(ctx, IS_NULL + " " + OR + StringUtils.SPACE, " = ''", IS_EMPTY, isEmpty.getProperty());
        });

        addCriterionHandler(QueryModel.IsNotEmpty.class, (ctx, isNotEmpty) -> {
            if (getDialect() == Dialect.ORACLE) {
                // Oracle treats blank and null the same
                QueryPropertyPath propertyPath = ctx.getRequiredProperty(isNotEmpty.getProperty(), QueryModel.IsEmpty.class);
                StringBuilder whereClause = ctx.query();
                if (propertyPath.getProperty().isAssignable(CharSequence.class)) {
                    appendPropertyRef(whereClause, propertyPath);
                    whereClause.append(IS_NOT_NULL);
                } else {
                    appendPropertyRef(whereClause, propertyPath);
                    whereClause.append(IS_NOT_EMPTY);
                }
            } else {
                appendEmptyExpression(ctx, IS_NOT_NULL + " " + AND + StringUtils.SPACE, " <> ''", IS_NOT_EMPTY, isNotEmpty.getProperty());
            }
        });

        addCriterionHandler(QueryModel.IdEquals.class, (ctx, idEquals) -> {
            StringBuilder whereClause = ctx.query();
            PersistentEntity persistentEntity = ctx.getPersistentEntity();
            if (persistentEntity.hasCompositeIdentity()) {
                for (PersistentProperty prop : persistentEntity.getCompositeIdentity()) {
                    appendCriteriaForOperator(
                        whereClause,
                        ctx,
                        null,
                        asQueryPropertyPath(ctx.getCurrentTableAlias(), prop),
                        idEquals.getValue(),
                        " = "
                    );
                    whereClause.append(LOGICAL_AND);
                }
                whereClause.setLength(whereClause.length() - LOGICAL_AND.length());
            } else if (persistentEntity.hasIdentity()) {
                appendCriteriaForOperator(whereClause,
                    ctx,
                    ctx.getRequiredProperty(persistentEntity.getIdentity().getName(), idEquals.getClass()),
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
                ctx.query(),
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
        addCriterionHandler(QueryModel.Like.class, valueComparison(" LIKE "));
        addCriterionHandler(QueryModel.ILike.class, (context, criterion) -> {
            if (getDialect() == Dialect.POSTGRES) {
                valueComparison(" ILIKE ").handle(context, criterion);
            } else {
                caseInsensitiveValueComparison(" LIKE ").handle(context, criterion);
            }
        });

        addCriterionHandler(QueryModel.Between.class, (ctx, between) -> {
            QueryPropertyPath prop = ctx.getRequiredProperty(between);
            StringBuilder whereClause = ctx.query();
            whereClause.append(OPEN_BRACKET);
            appendPropertyRef(whereClause, prop);
            whereClause.append(GREATER_THAN_OR_EQUALS);
            appendPlaceholderOrLiteral(ctx, prop, between.getFrom());
            whereClause.append(LOGICAL_AND);
            appendPropertyRef(whereClause, prop);
            whereClause.append(LESS_THAN_OR_EQUALS);
            appendPlaceholderOrLiteral(ctx, prop, between.getTo());
            whereClause.append(CLOSE_BRACKET);
        });

        addCriterionHandler(QueryModel.StartsWith.class, likeConcatComparison("?", "'%'"));
        addCriterionHandler(QueryModel.Contains.class, likeConcatComparison("'%'", "?", "'%'"));
        addCriterionHandler(QueryModel.EndsWith.class, likeConcatComparison("'%'", "?"));

        addCriterionHandler(QueryModel.In.class, (ctx, inQuery) -> {
            QueryPropertyPath propertyPath = ctx.getRequiredProperty(inQuery.getProperty(), QueryModel.In.class);
            StringBuilder whereClause = ctx.query();
            appendPropertyRef(whereClause, propertyPath);
            whereClause.append(" IN (");
            Object value = inQuery.getValue();
            if (value instanceof BindingParameter) {
                ctx.pushParameter((BindingParameter) value, newBindingContext(propertyPath.propertyPath).expandable());
            } else {
                asLiterals(ctx.query(), value);
            }
            whereClause.append(CLOSE_BRACKET);
        });

        addCriterionHandler(QueryModel.NotIn.class, (ctx, inQuery) -> {
            QueryPropertyPath propertyPath = ctx.getRequiredProperty(inQuery.getProperty(), QueryModel.In.class);
            StringBuilder whereClause = ctx.query();
            appendPropertyRef(whereClause, propertyPath);
            whereClause.append(" NOT IN (");
            Object value = inQuery.getValue();
            if (value instanceof BindingParameter) {
                ctx.pushParameter((BindingParameter) value, newBindingContext(propertyPath.propertyPath).expandable());
            } else {
                asLiterals(ctx.query(), value);
            }
            whereClause.append(CLOSE_BRACKET);
        });

        addCriterionHandler(QueryModel.ArrayContains.class, (ctx, criterion) -> {
           throw new UnsupportedOperationException("ArrayContains is not supported by this implementation.");
        });
    }

    /**
     * Get dialect.
     *
     * @return dialect
     */
    protected Dialect getDialect() {
        return Dialect.ANSI;
    }

    /**
     * Appends values as literals to the sql query builder.
     *
     * @param sb the sql string builder
     * @param value the value to be added
     */
    protected void asLiterals(StringBuilder sb, @Nullable Object value) {
        if (value instanceof Iterable) {
            for (Iterator iterator = ((Iterable) value).iterator(); iterator.hasNext(); ) {
                Object o = iterator.next();
                sb.append(asLiteral(o));
                if (iterator.hasNext()) {
                    sb.append(",");
                }
            }
        } else if (value instanceof Object[]) {
            Object[] objects = (Object[]) value;
            for (int i = 0; i < objects.length; i++) {
                Object o = objects[i];
                sb.append(asLiteral(o));
                if (i + 1 != objects.length) {
                    sb.append(",");
                }
            }
        } else {
            sb.append(asLiteral(value));
        }
    }

    /**
     * Convert the literal value to it's SQL representation.
     *
     * @param value The literal value
     * @return converter value
     */
    @NonNull
    protected String asLiteral(@Nullable Object value) {
        if (value instanceof LiteralExpression) {
            value = ((LiteralExpression<?>) value).getValue();
        }
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return Long.toString(((Number) value).longValue());
        }
        if (value instanceof Boolean) {
            return value.toString().toUpperCase(Locale.ROOT);
        }
        return "'" + value + "'";
    }

    private <T extends QueryModel.PropertyCriterion> CriterionHandler<T> likeConcatComparison(String... parts) {
        return (ctx, propertyCriterion) -> {
            QueryPropertyPath propertyPath = ctx.getRequiredProperty(propertyCriterion);
            boolean isPostgres = getDialect() == Dialect.POSTGRES;
            StringBuilder query = ctx.query();
            if (propertyCriterion.isIgnoreCase() && !isPostgres) {
                query.append("LOWER(");
                appendPropertyRef(query, propertyPath);
                query.append(")");
            } else {
                appendPropertyRef(query, propertyPath);
            }
            if (isPostgres) {
                query.append(" ILIKE ");
            } else {
                query.append(" LIKE ");
            }
            concat(query, Arrays.stream(parts).map(p -> {
                if ("?".equals(p)) {
                    if (propertyCriterion.isIgnoreCase() && !isPostgres) {
                        return (Runnable) () -> {
                            query.append("LOWER(");
                            appendPlaceholderOrLiteral(ctx, propertyPath, propertyCriterion.getValue());
                            query.append(")");
                        };
                    } else {
                        return (Runnable) () -> appendPlaceholderOrLiteral(ctx, propertyPath, propertyCriterion.getValue());
                    }
                }
                return (Runnable) () -> query.append(p);
            }).collect(Collectors.toList()));
        };
    }

    private <T extends QueryModel.PropertyCriterion> CriterionHandler<T> valueComparison(String op) {
        return (ctx, propertyCriterion) -> {
            QueryPropertyPath prop = ctx.getRequiredProperty(propertyCriterion);
            appendCriteriaForOperator(ctx.query(), ctx, prop, propertyCriterion.getValue(), op);
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
            appendPropertyRef(ctx.query(), ctx.getRequiredProperty(expressionCriterion));
            ctx.query().append(expression);
        };
    }

    private QueryPropertyPath asQueryPropertyPath(String tableAlias, PersistentProperty persistentProperty) {
        return new QueryPropertyPath(asPersistentPropertyPath(persistentProperty), tableAlias);
    }

    private PersistentPropertyPath asPersistentPropertyPath(PersistentProperty persistentProperty) {
        return PersistentPropertyPath.of(Collections.emptyList(), persistentProperty, persistentProperty.getName());
    }

    /**
     * @param writer The writer
     * @param partsWriters The parts writers
     */
    protected void concat(StringBuilder writer, Collection<Runnable> partsWriters) {
        writer.append("CONCAT(");
        for (Iterator<Runnable> iterator = partsWriters.iterator(); iterator.hasNext(); ) {
            Runnable partWriter = iterator.next();
            partWriter.run();
            if (iterator.hasNext()) {
                writer.append(",");
            }
        }
        writer.append(")");
    }

    private void appendEmptyExpression(CriteriaContext ctx, String charSequencePrefix, String charSequenceSuffix, String listSuffix, String name) {
        QueryPropertyPath propertyPath = ctx.getRequiredProperty(name, QueryModel.IsEmpty.class);
        StringBuilder whereClause = ctx.query();
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

    @Override
    public QueryResult buildQuery(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query) {
        ArgumentUtils.requireNonNull("annotationMetadata", annotationMetadata);
        ArgumentUtils.requireNonNull("query", query);
        QueryState queryState = newQueryState(query, true, true);

        List<JoinPath> joinPaths = new ArrayList<>(query.getJoinPaths());
        joinPaths.sort((o1, o2) -> Comparator.comparingInt(String::length).thenComparing(String::compareTo).compare(o1.getPath(), o2.getPath()));
        for (JoinPath joinPath : joinPaths) {
            queryState.applyJoin(joinPath);
        }

        StringBuilder select = new StringBuilder(SELECT_CLAUSE);
        buildSelectClause(query, queryState, select);
        appendForUpdate(QueryPosition.AFTER_TABLE_NAME, query, select);
        queryState.getQuery().insert(0, select);

        QueryModel.Junction criteria = query.getCriteria();

        if (!criteria.isEmpty() || annotationMetadata.hasStereotype(WhereSpecifications.class) || queryState.getEntity().getAnnotationMetadata().hasStereotype(WhereSpecifications.class)) {
            buildWhereClause(annotationMetadata, criteria, queryState);
        }

        appendOrder(query, queryState);
        appendForUpdate(QueryPosition.END_OF_QUERY, query, queryState.getQuery());

        return QueryResult.of(
            queryState.getFinalQuery(),
            queryState.getQueryParts(),
            queryState.getParameterBindings(),
            queryState.getAdditionalRequiredParameters(),
            query.getMax(),
            query.getOffset(),
            queryState.getJoinPaths()
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

            // if "root association" has a declared alias, don't add entity alias as a prefix to match behavior of @Join(alias= "...")
            if (joinPath.getAssociationPath()[0].hasDeclaredAliasName()) {
                return joinPathAlias;
            }

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
            String p = "";
            for (Association ass : joinPath.getAssociationPath()) {
                p += ass.getAliasName();
                if (ass.hasDeclaredAliasName() && ass != joinPath.getAssociation()) {
                    p += "_";
                }
            }
            return p;
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

    @Internal
    /**
     * Does nothing but subclasses might override and implement new behavior.
     */
    protected void selectAllColumnsFromJoinPaths(QueryState queryState,
                                                 StringBuilder queryBuffer,
                                                 Collection<JoinPath> allPaths,
                                                 @Nullable
                                                 Map<JoinPath, String> joinAliasOverride) {
    }

    /**
     * Begins the query state.
     *
     * @param query      The query
     * @param allowJoins Whether joins are allowed
     * @param useAlias   Whether alias should be used
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

    /**
     * Build select statement.
     * @param queryState the query state
     * @param queryString the query string builder
     * @param projectionList projection list (can be empty, then selects all columns)
     * @param tableAlias the table alias
     * @param entity the persistent entity
     */
    protected void buildSelect(QueryState queryState, StringBuilder queryString, List<QueryModel.Projection> projectionList, String tableAlias, PersistentEntity entity) {
        if (projectionList.isEmpty()) {
            selectAllColumns(queryState, queryString);
        } else {
            for (Iterator i = projectionList.iterator(); i.hasNext(); ) {
                QueryModel.Projection projection = (QueryModel.Projection) i.next();
                if (projection instanceof QueryModel.LiteralProjection) {
                    queryString.append(asLiteral(((QueryModel.LiteralProjection) projection).getValue()));
                } else if (projection instanceof QueryModel.CountProjection) {
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
                            if (!appendAssociationProjection(queryState, queryString, property, propertyPath)) {
                                continue;
                            }
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
        boolean[] needsTrimming = {false};
        traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), (associations, property) -> {
            String columnName = getMappedName(namingStrategy, associations, property);
            if (escape) {
                columnName = quote(columnName);
            }
            sb.append(tableAlias).append(DOT).append(columnName);
            String columnAlias = getColumnAlias(property);
            if (StringUtils.isNotEmpty(columnAlias)) {
                sb.append(AS_CLAUSE).append(columnAlias);
            }
            sb.append(COMMA);
            needsTrimming[0] = true;
        });
        if (needsTrimming[0]) {
            sb.setLength(sb.length() - 1);
        }
    }

    /**
     * Appends selection projection for the property which is association.
     *
     * @param queryState the query state
     * @param queryString the query string builder
     * @param property the persistent property
     * @param propertyPath the persistent property path
     * @return true if association projection is appended, otherwise false
     */
    protected boolean appendAssociationProjection(QueryState queryState, StringBuilder queryString, PersistentProperty property, PersistentPropertyPath propertyPath) {
        String joinedPath = propertyPath.getPath();
        if (!queryState.isJoined(joinedPath)) {
            queryString.setLength(queryString.length() - 1);
            return false;
        }
        String joinAlias = queryState.computeAlias(propertyPath.getPath());
        selectAllColumns(((Association) property).getAssociatedEntity(), joinAlias, queryString);
        Collection<JoinPath> joinPaths = queryState.getQueryModel().getJoinPaths();
        List<JoinPath> newJoinPaths = new ArrayList<>(joinPaths.size());
        Map<JoinPath, String> joinAliasOverride = new HashMap<>();
        Map<JoinPath, String> columnAliasOverride = new HashMap<>();
        for (JoinPath joinPath : joinPaths) {
            if (joinPath.getPath().startsWith(joinedPath) && !joinPath.getPath().equals(joinedPath)) {
                int removedItems = 1;
                for (int k = 0; k < joinedPath.length(); k++) {
                    if (joinedPath.charAt(k) == '.') {
                        removedItems++;
                    }
                }
                JoinPath newJoinPath = new JoinPath(
                    joinPath.getPath().substring(joinedPath.length() + 1),
                    Arrays.copyOfRange(joinPath.getAssociationPath(), removedItems, joinPath.getAssociationPath().length),
                    joinPath.getJoinType(),
                    joinPath.getAlias().orElse(null)
                );
                newJoinPaths.add(newJoinPath);
                joinAliasOverride.put(newJoinPath, getAliasName(joinPath));
                columnAliasOverride.put(newJoinPath, getPathOnlyAliasName(joinPath));
            }
        }
        queryState.setJoinPaths(newJoinPaths);
        selectAllColumnsFromJoinPaths(queryState, queryString, newJoinPaths, joinAliasOverride);
        return true;
    }

    /**
     * Gets {@link NamingStrategy} for the property path. Subclasses might override and potentially
     * provide different strategy in some cases.
     *
     * @param propertyPath the property path representation
     * @return naming strategy for the property path
     */
    protected NamingStrategy getNamingStrategy(PersistentPropertyPath propertyPath) {
        return propertyPath.getNamingStrategy();
    }

    /**
     * Gets {@link NamingStrategy} for the entity. Subclasses might override and potentially
     * provide different strategy in some cases.
     *
     * @param entity the persistent entity
     * @return naming strategy for the entity
     */
    protected NamingStrategy getNamingStrategy(PersistentEntity entity) {
        return entity.getNamingStrategy();
    }

    /**
     * Gets the mapped name from the property using {@link NamingStrategy}.
     *
     * @param namingStrategy the naming strategy being used
     * @param property the persistent property
     * @return the mapped name for the property
     */
    protected @NonNull String getMappedName(@NonNull NamingStrategy namingStrategy, @NonNull PersistentProperty property) {
        return namingStrategy.mappedName(property);
    }

    /**
     * Gets the mapped name from the association using {@link NamingStrategy}.
     *
     * @param namingStrategy the naming strategy being used
     * @param association the associatioon
     * @return the mapped name for the association
     */
    protected @NonNull String getMappedName(@NonNull NamingStrategy namingStrategy, @NonNull Association association) {
        return namingStrategy.mappedName(association);
    }

    /**
     * Gets the mapped name from for the list of associations and property using {@link NamingStrategy}.
     *
     * @param namingStrategy the naming strategy
     * @param associations the association list
     * @param property the property
     * @return the mappen name for the list of associations and property using given naming strategy
     */
    protected @NonNull String getMappedName(@NonNull NamingStrategy namingStrategy, @NonNull List<Association> associations, @NonNull PersistentProperty property) {
        return namingStrategy.mappedName(associations, property);
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
            columnName = getMappedName(getNamingStrategy(entity), propertyPath.getAssociations(), propertyPath.getProperty());
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

    /**
     * Builds where clause.
     *
     * @param annotationMetadata the annotation metadata for the method
     * @param criteria the criteria
     * @param queryState the query state
     */
    protected void buildWhereClause(AnnotationMetadata annotationMetadata, QueryModel.Junction criteria, QueryState queryState) {
        StringBuilder queryClause = queryState.getQuery();
        if (!criteria.isEmpty()) {
            queryClause.append(WHERE_CLAUSE);
            if (criteria instanceof QueryModel.Negation) {
                queryClause.append(NOT);
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

            queryClause.append(OPEN_BRACKET);
            handleJunction(ctx, criteria);

            StringBuilder additionalWhereBuff = buildAdditionalWhereClause(queryState, annotationMetadata);
            appendAdditionalWhere(queryClause, queryState, additionalWhereBuff.toString());

        } else {
            StringBuilder additionalWhereBuff = buildAdditionalWhereClause(queryState, annotationMetadata);
            if (additionalWhereBuff.length() > 0) {
                queryClause.append(WHERE_CLAUSE)
                    .append(OPEN_BRACKET)
                    .append(additionalWhereBuff.toString())
                    .append(CLOSE_BRACKET);
            }
        }
    }

    private StringBuilder buildAdditionalWhereClause(QueryState queryState, AnnotationMetadata annotationMetadata) {
        StringBuilder additionalWhereBuff = new StringBuilder(buildAdditionalWhereString(queryState.getRootAlias(), queryState.getEntity(), annotationMetadata));
        List<JoinPath> joinPaths = queryState.getJoinPaths();
        if (CollectionUtils.isNotEmpty(joinPaths)) {
            Set<String> addedJoinPaths = new HashSet<>();
            for (JoinPath joinPath : joinPaths) {
                String path = joinPath.getPath();
                if (addedJoinPaths.contains(path)) {
                    continue;
                }
                addedJoinPaths.add(path);
                String joinAdditionalWhere = buildAdditionalWhereString(joinPath, annotationMetadata);
                if (StringUtils.isNotEmpty(joinAdditionalWhere)) {
                    if (additionalWhereBuff.length() > 0) {
                        additionalWhereBuff.append(SPACE).append(AND).append(SPACE);
                    }
                    additionalWhereBuff.append(joinAdditionalWhere);
                }
            }
        }
        return additionalWhereBuff;
    }

    private void appendAdditionalWhere(StringBuilder queryClause, QueryState queryState, String additionalWhere) {
        String queryStr = queryClause.toString();
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
        if (queryStr.endsWith(WHERE_CLAUSE + OPEN_BRACKET)) {
            if (StringUtils.isNotEmpty(additionalWhere)) {
                queryClause.append(additionalWhere).append(CLOSE_BRACKET);
            }
        } else {
            if (StringUtils.isNotEmpty(additionalWhere)) {
                queryClause.append(LOGICAL_AND).append(OPEN_BRACKET).append(additionalWhere).append(CLOSE_BRACKET);
            }
            queryClause.append(CLOSE_BRACKET);
        }
    }

    private String buildAdditionalWhereString(String alias, PersistentEntity entity, AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.hasAnnotation(IgnoreWhere.class)) {
            return "";
        }
        final String whereStr = resolveWhereForAnnotationMetadata(alias, annotationMetadata);
        if (StringUtils.isNotEmpty(whereStr)) {
            return whereStr;
        } else {
            return resolveWhereForAnnotationMetadata(alias, entity.getAnnotationMetadata());
        }
    }

    private String buildAdditionalWhereString(JoinPath joinPath, AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.hasAnnotation(IgnoreWhere.class)) {
            return "";
        }
        Association association = joinPath.getAssociation();
        if (association == null) {
            return "";
        }
        String alias = getAliasName(joinPath);
        return resolveWhereForAnnotationMetadata(alias, association.getAssociatedEntity().getAnnotationMetadata());
    }

    private String resolveWhereForAnnotationMetadata(String alias, AnnotationMetadata annotationMetadata) {
        return annotationMetadata.getAnnotationValuesByType(Where.class)
            .stream()
            .map(av -> av.stringValue().orElse(null))
            .map(val -> replaceAlias(alias, val))
            .filter(StringUtils::isNotEmpty)
            .collect(Collectors.joining(LOGICAL_AND));
    }

    /**
     * Appends order to the query.
     *
     * @param query the query model
     * @param queryState the query state
     */
    protected void appendOrder(QueryModel query, QueryState queryState) {
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

    /**
     * Adds "forUpdate" pisimmistic locking.
     *
     * @param queryPosition The query position
     * @param query         The query
     * @param queryBuilder  The builder
     */
    protected void appendForUpdate(QueryPosition queryPosition, QueryModel query, StringBuilder queryBuilder) {
        if (query.isForUpdate() && !supportsForUpdate()) {
            throw new IllegalStateException("For update not supported for current query builder: " + getClass().getSimpleName());
        }
    }

    private void handleJunction(CriteriaContext ctx, QueryModel.Junction criteria) {
        StringBuilder whereClause = ctx.query();
        final String operator = criteria instanceof QueryModel.Conjunction ? LOGICAL_AND : LOGICAL_OR;
        for (Iterator<QueryModel.Criterion> iterator = criteria.getCriteria().iterator(); iterator.hasNext(); ) {
            QueryModel.Criterion criterion = iterator.next();
            CriterionHandler<QueryModel.Criterion> criterionHandler = queryHandlers.get(criterion.getClass());
            if (criterionHandler == null) {
                throw new IllegalArgumentException("Queries of type " + criterion.getClass().getSimpleName() + " are not supported by this implementation");
            }
            criterionHandler.handle(ctx, criterion);
            if (iterator.hasNext()) {
                whereClause.append(operator);
            }
        }
    }

    private void appendCriteriaForOperator(StringBuilder whereClause,
                                           PropertyParameterCreator propertyParameterCreator,
                                           QueryPropertyPath propertyPath,
                                           Object value,
                                           String operator) {
        appendCriteriaForOperator(whereClause, propertyParameterCreator, propertyPath.propertyPath, propertyPath, value, operator);
    }

    private void appendCriteriaForOperator(StringBuilder whereClause,
                                           PropertyParameterCreator propertyParameterCreator,
                                           PersistentPropertyPath parameterPropertyPath,
                                           QueryPropertyPath propertyPath,
                                           Object value,
                                           String operator) {

        if (value instanceof BindingParameter) {
            BindingParameter bindingParameter = (BindingParameter) value;
            boolean computePropertyPaths = computePropertyPaths();
            if (!computePropertyPaths) {
                appendPropertyRef(whereClause, propertyPath);
                whereClause.append(operator);
                propertyParameterCreator.pushParameter(
                    bindingParameter,
                    newBindingContext(parameterPropertyPath, propertyPath.propertyPath)
                );
                return;
            }

            String currentAlias = propertyPath.getTableAlias();
            NamingStrategy namingStrategy = propertyPath.getNamingStrategy();
            boolean shouldEscape = propertyPath.shouldEscape();

            boolean[] needsTrimming = {false};
            traversePersistentPropertiesForCriteria(propertyPath.getAssociations(), propertyPath.getProperty(), (associations, property) -> {
                String readTransformer = getDataTransformerReadValue(currentAlias, property).orElse(null);
                if (readTransformer != null) {
                    whereClause.append(readTransformer);
                } else {
                    if (currentAlias != null) {
                        whereClause.append(currentAlias).append(DOT);
                    }
                    String columnName = getMappedName(namingStrategy, associations, property);
                    if (shouldEscape) {
                        columnName = quote(columnName);
                    }
                    whereClause.append(columnName);
                }

                whereClause.append(operator);
                propertyParameterCreator.pushParameter(
                    bindingParameter,
                    newBindingContext(parameterPropertyPath, PersistentPropertyPath.of(associations, property))
                );
                whereClause.append(LOGICAL_AND);
                needsTrimming[0] = true;
            });

            if (needsTrimming[0]) {
                whereClause.setLength(whereClause.length() - LOGICAL_AND.length());
            }

        } else {
            appendPropertyRef(whereClause, propertyPath);
            whereClause.append(operator).append(asLiteral(value));
        }
    }

    /**
     * Appends property to the sql string builder.
     * @param sb the sql string builder
     * @param propertyPath the query property path
     */
    protected void appendPropertyRef(StringBuilder sb, QueryPropertyPath propertyPath) {
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
        StringBuilder whereClause = ctx.query();
        whereClause.append("LOWER(");
        appendPropertyRef(whereClause, propertyPath);
        whereClause.append(")")
            .append(operator)
            .append("LOWER(");
        appendPlaceholderOrLiteral(ctx, propertyPath, criterion.getValue());
        whereClause.append(")");
    }

    private void appendPlaceholderOrLiteral(CriteriaContext ctx, QueryPropertyPath propertyPath, Object value) {
        if (value instanceof BindingParameter) {
            ctx.pushParameter((BindingParameter) value, newBindingContext(propertyPath.propertyPath));
            return;
        }
        ctx.query().append(asLiteral(value));
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
        StringBuilder whereClause = ctx.query();
        appendPropertyRef(whereClause, propertyPath);
        whereClause.append(comparisonExpression);
        // TODO: support subqueryCriterion
        whereClause.append(CLOSE_BRACKET);
    }

    private void buildUpdateStatement(QueryState queryState, Map<String, Object> propertiesToUpdate) {
        StringBuilder queryString = queryState.getQuery();
        queryString.append(SPACE).append("SET").append(SPACE);

        // keys need to be sorted before query is built

        List<Map.Entry<QueryPropertyPath, Object>> update = propertiesToUpdate.entrySet().stream()
            .map(e -> {
                QueryPropertyPath propertyPath = findProperty(queryState, e.getKey(), null);
                if (propertyPath.getProperty() instanceof Association && ((Association) propertyPath.getProperty()).isForeignKey()) {
                    throw new IllegalArgumentException("Foreign key associations cannot be updated as part of a batch update statement");
                }
                return new AbstractMap.SimpleEntry<>(propertyPath, e.getValue());
            })
            .filter(e -> !(e.getValue() instanceof QueryParameter) || !e.getKey().getProperty().isGenerated())
            .collect(Collectors.toList());

        boolean[] needsTrimming = {false};
        if (!computePropertyPaths()) {
            for (Map.Entry<QueryPropertyPath, Object> entry : update) {
                QueryPropertyPath propertyPath = entry.getKey();
                PersistentProperty prop = propertyPath.getProperty();
                String tableAlias = propertyPath.getTableAlias();
                if (tableAlias != null) {
                    queryString.append(tableAlias).append(DOT);
                }
                queryString.append(propertyPath.getPath()).append('=');
                if (entry.getValue() instanceof BindingParameter) {
                    appendUpdateSetParameter(queryString, tableAlias, prop, () -> {
                        queryState.pushParameter((BindingParameter) entry.getValue(), newBindingContext(propertyPath.propertyPath));
                    });
                } else {
                    queryString.append(asLiteral(entry.getValue()));
                }
                queryString.append(COMMA);
                needsTrimming[0] = true;
            }
        } else {
            NamingStrategy namingStrategy = getNamingStrategy(queryState.getEntity());
            for (Map.Entry<QueryPropertyPath, Object> entry : update) {
                QueryPropertyPath propertyPath = entry.getKey();
                if (entry.getValue() instanceof BindingParameter) {
                    traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), (associations, property) -> {
                        String tableAlias = propertyPath.getTableAlias();
                        if (tableAlias != null) {
                            queryString.append(tableAlias).append(DOT);
                        }
                        String columnName = getMappedName(namingStrategy, associations, property);
                        if (queryState.escape) {
                            columnName = quote(columnName);
                        }
                        queryString.append(columnName).append('=');
                        appendUpdateSetParameter(queryString, tableAlias, property, () -> {
                            queryState.pushParameter(
                                (BindingParameter) entry.getValue(),
                                newBindingContext(
                                    propertyPath.propertyPath,
                                    PersistentPropertyPath.of(associations, property, asPath(associations, property))
                                )
                            );
                        });
                        queryString.append(COMMA);
                        needsTrimming[0] = true;
                    });
                } else {
                    String tableAlias = propertyPath.getTableAlias();
                    if (tableAlias != null) {
                        queryString.append(tableAlias).append(DOT);
                    }
                    queryString.append(propertyPath.getColumnName()).append('=');
                    queryString.append(asLiteral(entry.getValue()));
                    queryString.append(COMMA);
                    needsTrimming[0] = true;
                }
            }
        }
        if (needsTrimming[0]) {
            queryString.setLength(queryString.length() - 1);
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
     * @param sb              The string builder
     * @param alias           The alias
     * @param prop            The property
     * @param appendParameter The append parameter action
     */
    protected void appendUpdateSetParameter(StringBuilder sb, String alias, PersistentProperty prop, Runnable appendParameter) {
        Optional<String> dataTransformerWriteValue = getDataTransformerWriteValue(alias, prop);
        if (dataTransformerWriteValue.isPresent()) {
            appendTransformed(sb, dataTransformerWriteValue.get(), appendParameter);
        } else {
            appendParameter.run();
        }
    }

    /**
     * Appends custom query part.
     *
     * @param sb              The string builder
     * @param transformed     The transformed query part
     * @param appendParameter The append parameter action
     */
    protected void appendTransformed(StringBuilder sb, String transformed, Runnable appendParameter) {
        int parameterPosition = transformed.indexOf("?");
        if (parameterPosition > -1) {
            if (transformed.lastIndexOf("?") != parameterPosition) {
                throw new IllegalStateException("Only one parameter placeholder is allowed!");
            }
            sb.append(transformed, 0, parameterPosition);
            appendParameter.run();
            sb.append(transformed.substring(parameterPosition + 1));
        } else {
            sb.append(transformed);
        }
    }

    private void appendPropertyComparison(CriteriaContext ctx, QueryModel.PropertyComparisonCriterion comparisonCriterion, String operator) {
        StringBuilder sb = ctx.query();
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
            String lastJoinAlias = null;
            for (Association association : propertyPath.getAssociations()) {
                joinPathJoiner.add(association.getName());
                if (association instanceof Embedded) {
                    continue;
                }
                if (joinAssociation == null) {
                    joinAssociation = association;
                    continue;
                }
                if (association != joinAssociation.getAssociatedEntity().getIdentity()) {
                    if (!queryState.isAllowJoins()) {
                        throw new IllegalArgumentException("Joins cannot be used in a DELETE or UPDATE operation");
                    }
                    String joinStringPath = joinPathJoiner.toString();
                    if (!queryState.isJoined(joinStringPath)) {
                        throw new IllegalArgumentException("Property is not joined at path: " + joinStringPath);
                    }
                    lastJoinAlias = joinInPath(queryState, joinStringPath);
                    // Continue to look for a joined property
                    joinAssociation = association;
                } else {
                    // We don't need to join to access the id of the relation
                    joinAssociation = null;
                }
            }
            PersistentProperty property = propertyPath.getProperty();
            if (joinAssociation != null) {
                // We don't need to join to access the id of the relation if it is not a foreign key association
                if (property != joinAssociation.getAssociatedEntity().getIdentity() || joinAssociation.isForeignKey()) {
                    String joinStringPath = joinPathJoiner.toString();
                    if (!queryState.isJoined(joinStringPath)) {
                        throw new IllegalArgumentException("Property is not joined at path: " + joinStringPath);
                    }
                    if (lastJoinAlias == null) {
                        lastJoinAlias = joinInPath(queryState, joinPathJoiner.toString());
                    }
                }
                if (lastJoinAlias != null) {
                    // 'joinPath.prop' should be represented as a path of 'prop' with a join alias
                    return new QueryPropertyPath(
                        new PersistentPropertyPath(Collections.emptyList(), property, property.getName()),
                        lastJoinAlias
                    );
                }
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
    public QueryResult buildUpdate(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query,
                                   @NonNull List<String> propertiesToUpdate) {
        return buildUpdate(annotationMetadata,
            query,
            propertiesToUpdate.stream().collect(Collectors.toMap(
                prop -> prop,
                QueryParameter::new,
                (a, b) -> a,
                () -> new LinkedHashMap<>()))
        );
    }

    @Override
    public QueryResult buildUpdate(@NonNull AnnotationMetadata annotationMetadata, @NonNull QueryModel query,
                                   @NonNull Map<String, Object> propertiesToUpdate) {
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
            queryState.getFinalQuery(),
            queryState.getQueryParts(),
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
            queryState.getFinalQuery(),
            queryState.getQueryParts(),
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
        return buildOrderBy("", entity, sort);
    }

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query  The query
     * @param entity The root entity
     * @param sort   The sort
     * @return The encoded query
     */
    @NonNull
    public QueryResult buildOrderBy(String query, @NonNull PersistentEntity entity, @NonNull Sort sort) {
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
            PersistentPropertyPath path = entity.getPropertyPath(property);
            if (path == null) {
                throw new IllegalArgumentException("Cannot sort on non-existent property path: " + property);
            }
            boolean ignoreCase = order.isIgnoreCase();
            if (ignoreCase) {
                buff.append("LOWER(");
            }
            if (path.getAssociations().isEmpty()) {
                buff.append(getAliasName(entity));
            } else {
                StringJoiner joiner = new StringJoiner(".");
                for (Association association : path.getAssociations()) {
                    joiner.add(association.getName());
                }
                String joinAlias = getAliasName(new JoinPath(joiner.toString(), path.getAssociations().toArray(new Association[0]), Join.Type.DEFAULT, null));
                if (!computePropertyPaths()) {
                    if (!query.contains(" " + joinAlias + " ") && !query.endsWith(" " + joinAlias)) {
                        // Special hack case for JPA, Hibernate can join the relation with cross join automatically when referenced by the property path
                        // This probably should be removed in the future major version
                        buff.append(getAliasName(entity)).append(DOT);
                        StringJoiner pathJoiner = new StringJoiner(".");
                        for (Association association : path.getAssociations()) {
                            pathJoiner.add(association.getName());
                        }
                        buff.append(pathJoiner);
                    } else {
                        buff.append(joinAlias);
                    }
                } else {
                    buff.append(joinAlias);
                }
            }
            buff.append(DOT);
            if (!computePropertyPaths()) {
                buff.append(path.getProperty().getName());
            } else {
                buff.append(getColumnName(path.getProperty()));
            }
            if (ignoreCase) {
                buff.append(")");
            }
            buff.append(SPACE).append(order.getDirection());
            if (i.hasNext()) {
                buff.append(",");
            }
        }

        return QueryResult.of(
            buff.toString(),
            Collections.emptyList(),
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
     * Traverses persistent properties.
     *
     * @param property The property to start traversing from
     * @param consumer The function to invoke on every property
     */
    protected void traversePersistentProperties(PersistentProperty property, BiConsumer<List<Association>, PersistentProperty> consumer) {
        traversePersistentProperties(Collections.emptyList(), property, consumer);
    }

    /**
     * Traverses persistent properties.
     *
     * @param persistentEntity The persistent entity
     * @param consumer         The function to invoke on every property
     */
    protected void traversePersistentProperties(PersistentEntity persistentEntity, BiConsumer<List<Association>, PersistentProperty> consumer) {
        PersistentEntityUtils.traversePersistentProperties(persistentEntity, consumer);
    }

    /**
     * Traverses persistent properties.
     *
     * @param persistentEntity The persistent entity
     * @param includeIdentity  Should be identifier included
     * @param includeVersion   Should be version included
     * @param consumer         The function to invoke on every property
     */
    protected void traversePersistentProperties(PersistentEntity persistentEntity, boolean includeIdentity, boolean includeVersion, BiConsumer<List<Association>, PersistentProperty> consumer) {
        PersistentEntityUtils.traversePersistentProperties(persistentEntity, includeIdentity, includeVersion, consumer);
    }

    /**
     * Traverses persistent properties used in criteria (where clause).
     *
     * @param associations      The association list being traversed with the property
     * @param property          The persistent property
     * @param consumerProperty  The function to invoke on every property
     */
    private void traversePersistentPropertiesForCriteria(List<Association> associations,
                                              PersistentProperty property,
                                              BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        PersistentEntityUtils.traversePersistentProperties(associations, property, consumerProperty);
    }

    /**
     * Traverses persistent properties.
     *
     * @param associations      The association list being traversed with the property
     * @param property          The persistent property
     * @param consumerProperty  The function to invoke on every property
     */
    protected void traversePersistentProperties(List<Association> associations,
                                              PersistentProperty property,
                                              BiConsumer<List<Association>, PersistentProperty> consumerProperty) {
        PersistentEntityUtils.traversePersistentProperties(associations, property, consumerProperty);
    }

    private Optional<String> getDataTransformerValue(String alias, PersistentProperty prop, String val) {
        return prop.getAnnotationMetadata()
            .stringValue(DataTransformer.class, val)
            .map(v -> replaceAlias(alias, v));
    }

    private String replaceAlias(String alias, String v) {
        return v.replaceAll(ALIAS_REPLACE_QUOTED, alias == null ? "" : alias + ".");
    }

    private BindingParameter.BindingContext newBindingContext(@Nullable PersistentPropertyPath ref,
                                                              @Nullable PersistentPropertyPath persistentPropertyPath) {
        return BindingParameter.BindingContext.create()
            .incomingMethodParameterProperty(ref)
            .outgoingQueryParameterProperty(persistentPropertyPath);
    }

    /**
     * Creates new binding parameter context.
     *
     * @param ref the persistent property reference
     * @return new binding parameter context
     */
    protected BindingParameter.BindingContext newBindingContext(@Nullable PersistentPropertyPath ref) {
        return BindingParameter.BindingContext.create()
            .incomingMethodParameterProperty(ref)
            .outgoingQueryParameterProperty(ref);
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
     * @param clazz   The handler class
     * @param handler The handler
     * @param <T>     The criterion type
     */
    protected <T extends QueryModel.Criterion> void addCriterionHandler(Class<T> clazz, CriterionHandler<T> handler) {
        queryHandlers.put(clazz, handler);
    }

    /**
     * Gets column alias if defined as alias field on MappedProperty annotation on the mapping field.
     *
     * @param property the persisent propert
     * @return column alias if defined, otherwise an empty string
     */
    protected final String getColumnAlias(PersistentProperty property) {
        return property.getAnnotationMetadata().stringValue(MappedProperty.class, MappedProperty.ALIAS).orElse("");
    }

    /**
     * A criterion handler.
     *
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

        default void pushParameter(@NotNull BindingParameter bindingParameter, @NotNull BindingParameter.BindingContext bindingContext) {
            getQueryState().pushParameter(bindingParameter, bindingContext);
        }

        default QueryPropertyPath getRequiredProperty(QueryModel.PropertyNameCriterion propertyCriterion) {
            return getRequiredProperty(propertyCriterion.getProperty(), propertyCriterion.getClass());
        }

        default StringBuilder query() {
            return getQueryState().getQuery();
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
        private final List<String> queryParts = new ArrayList<>();
        private final boolean allowJoins;
        private final QueryModel queryObject;
        private final boolean escape;
        private final PersistentEntity entity;
        private List<JoinPath> joinPaths = new ArrayList<>();

        public QueryState(QueryModel query, boolean allowJoins, boolean useAlias) {
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
         * @return name A placeholder in a query
         */
        public String addAdditionalRequiredParameter(@NonNull String name) {
            Placeholder placeholder = newParameter();
            additionalRequiredParameters.put(placeholder.key, name);
            return placeholder.name;
        }

        public String getFinalQuery() {
            if (query.length() > 0) {
                queryParts.add(query.toString());
                query.setLength(0);
            }
            StringBuilder sb = new StringBuilder(queryParts.get(0));
            int i = 1;
            for (int k = 1; k < queryParts.size(); k++) {
                Placeholder placeholder = formatParameter(i++);
                sb.append(placeholder.name);
                sb.append(queryParts.get(k));
            }
            return sb.toString();
        }

        public List<String> getQueryParts() {
            return queryParts;
        }

        /**
         * @return The query string
         */
        public StringBuilder getQuery() {
            return query;
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
            joinPaths.add(jp);
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
         * @param associationPath The association path.
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
         * Checks if the path is joined already.
         *
         * @param associationPath The association path.
         * @return true if joined
         */
        public boolean isJoined(String associationPath) {
            for (String joinPath : appliedJoinPaths.keySet()) {
                if (joinPath.startsWith(associationPath)) {
                    return true;
                }
            }
            return appliedJoinPaths.containsKey(associationPath);
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
        public void pushParameter(@NotNull BindingParameter bindingParameter, @NotNull BindingParameter.BindingContext bindingContext) {
            Placeholder placeholder = newParameter();
            bindingContext = bindingContext
                .index(position.get() + 1)
                .name(placeholder.getKey());
            parameterBindings.add(
                bindingParameter.bind(bindingContext)
            );
            queryParts.add(query.toString());
            query.setLength(0);
        }

        public List<JoinPath> getJoinPaths() {
            return joinPaths;
        }

        public void setJoinPaths(List<JoinPath> joinPaths) {
            this.joinPaths = joinPaths;
        }
    }

    private interface PropertyParameterCreator {

        void pushParameter(@NotNull BindingParameter bindingParameter,
                           @NotNull BindingParameter.BindingContext bindingContext);

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
            String columnName = getMappedName(getNamingStrategy(), propertyPath.getAssociations(), propertyPath.getProperty());
            if (shouldEscape()) {
                return quote(columnName);
            }
            return columnName;
        }

        /**
         * @return the naming strategy
         */
        public NamingStrategy getNamingStrategy() {
            return AbstractSqlLikeQueryBuilder.this.getNamingStrategy(propertyPath);
        }

        /**
         * @return should escape
         */
        public boolean shouldEscape() {
            return AbstractSqlLikeQueryBuilder.this.shouldEscape(propertyPath.findPropertyOwner().orElse(propertyPath.getProperty().getOwner()));
        }

        /**
         * @return the persistent property path
         */
        public PersistentPropertyPath getPropertyPath() {
            return propertyPath;
        }
    }

    protected enum QueryPosition {
        AFTER_TABLE_NAME, END_OF_QUERY
    }
}
