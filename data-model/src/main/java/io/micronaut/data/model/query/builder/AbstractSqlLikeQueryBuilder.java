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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.DataAnnotationUtils;
import io.micronaut.data.annotation.DataTransformer;
import io.micronaut.data.annotation.EntityRepresentation;
import io.micronaut.data.annotation.IgnoreWhere;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.annotation.repeatable.WhereSpecifications;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.AssociationQuery;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.builder.sql.Dialect;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
@SuppressWarnings("FileLength")
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
    protected static final String RETURNING = " RETURNING ";
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
    protected static final String ALIAS_REPLACE_QUOTED = "@\\.";
    protected static final String JSON_COLUMN = "column";
    protected static final String CANNOT_QUERY_ON_ID_WITH_ENTITY_THAT_HAS_NO_ID = "Cannot query on ID with entity that has no ID";

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
                    appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
                    whereClause.append(IS_NOT_NULL);
                } else {
                    appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
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
                        ctx.getAnnotationMetadata(),
                        ctx.getPersistentEntity(),
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
                    ctx.getAnnotationMetadata(),
                    ctx.getPersistentEntity(),
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
                ctx.getAnnotationMetadata(),
                ctx.getPersistentEntity(),
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
            appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), prop);
            whereClause.append(GREATER_THAN_OR_EQUALS);
            appendPlaceholderOrLiteral(ctx, prop, between.getFrom());
            whereClause.append(LOGICAL_AND);
            appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), prop);
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
            appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
            whereClause.append(" IN (");
            Object value = inQuery.getValue();
            if (value instanceof BindingParameter bindingParameter) {
                ctx.pushParameter(bindingParameter, newBindingContext(propertyPath.propertyPath).expandable());
            } else {
                asLiterals(ctx.query(), value);
            }
            whereClause.append(CLOSE_BRACKET);
        });

        addCriterionHandler(QueryModel.NotIn.class, (ctx, inQuery) -> {
            QueryPropertyPath propertyPath = ctx.getRequiredProperty(inQuery.getProperty(), QueryModel.In.class);
            StringBuilder whereClause = ctx.query();
            appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
            whereClause.append(" NOT IN (");
            Object value = inQuery.getValue();
            if (value instanceof BindingParameter bindingParameter) {
                ctx.pushParameter(bindingParameter, newBindingContext(propertyPath.propertyPath).expandable());
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
        if (value instanceof Iterable<?> iterable) {
            for (Iterator<?> iterator = iterable.iterator(); iterator.hasNext(); ) {
                Object o = iterator.next();
                sb.append(asLiteral(o));
                if (iterator.hasNext()) {
                    sb.append(",");
                }
            }
        } else if (value instanceof Object[] objects) {
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
        if (value instanceof LiteralExpression<?> literalExpression) {
            value = literalExpression.getValue();
        }
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number number) {
            return Long.toString(number.longValue());
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
                appendPropertyRef(query, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
                query.append(")");
            } else {
                appendPropertyRef(query, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
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
            }).toList());
        };
    }

    private <T extends QueryModel.PropertyCriterion> CriterionHandler<T> valueComparison(String op) {
        return (ctx, propertyCriterion) -> {
            QueryPropertyPath prop = ctx.getRequiredProperty(propertyCriterion);
            appendCriteriaForOperator(ctx.query(), ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), ctx, prop, propertyCriterion.getValue(), op);
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
            appendPropertyRef(ctx.query(), ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), ctx.getRequiredProperty(expressionCriterion));
            ctx.query().append(expression);
        };
    }

    protected final QueryPropertyPath asQueryPropertyPath(String tableAlias, PersistentProperty persistentProperty) {
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
            appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
            whereClause.append(charSequencePrefix);
            appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
            whereClause.append(charSequenceSuffix);
        } else {
            appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
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
        buildSelectClause(annotationMetadata, query, queryState, select);
        appendForUpdate(QueryPosition.AFTER_TABLE_NAME, query, select);
        queryState.getQuery().insert(0, select);

        QueryModel.Junction criteria = query.getCriteria();

        if (!criteria.isEmpty() || annotationMetadata.hasStereotype(WhereSpecifications.class) || queryState.getEntity().getAnnotationMetadata().hasStereotype(WhereSpecifications.class)) {
            buildWhereClause(annotationMetadata, criteria, queryState);
        }

        appendOrder(annotationMetadata, query, queryState);
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
            var p = new StringBuilder();
            for (Association ass : joinPath.getAssociationPath()) {
                p.append(ass.getAliasName());
                if (ass.hasDeclaredAliasName() && ass != joinPath.getAssociation()) {
                    p.append('_');
                }
            }
            return p.toString();
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
     * @param annotationMetadata  The annotation metadata
     * @param queryState  The query state
     * @param queryBuffer
     */
    protected abstract void selectAllColumns(AnnotationMetadata annotationMetadata, QueryState queryState, StringBuilder queryBuffer);

    /**
     * Selects all columns for the given entity and alias.
     *
     * @param entity             The entity
     * @param alias              The alias
     * @param queryBuffer        The buffer to append the columns
     */
    protected void selectAllColumns(PersistentEntity entity, String alias, StringBuilder queryBuffer) {
        selectAllColumns(AnnotationMetadata.EMPTY_METADATA, entity, alias, queryBuffer);
    }

    /**
     * Selects all columns for the given entity and alias.
     *
     * @param annotationMetadata The annotation metadata
     * @param entity             The entity
     * @param alias              The alias
     * @param queryBuffer        The buffer to append the columns
     */
    protected abstract void selectAllColumns(AnnotationMetadata annotationMetadata, PersistentEntity entity, String alias, StringBuilder queryBuffer);

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

    protected final void appendProperty(StringBuilder sb,
                                        List<Association> associations,
                                        PersistentProperty property,
                                        NamingStrategy namingStrategy,
                                        String tableAlias,
                                        boolean escape) {
        String transformed = getDataTransformerReadValue(tableAlias, property).orElse(null);
        String columnAlias = getColumnAlias(property);
        boolean useAlias = StringUtils.isNotEmpty(columnAlias);
        if (transformed != null) {
            sb.append(transformed).append(AS_CLAUSE).append(useAlias ? columnAlias : property.getPersistedName());
        } else {
            String column = getMappedName(namingStrategy, associations, property);
            column = escapeColumnIfNeeded(column, escape);
            if (tableAlias == null) {
                sb.append(column);
            } else {
                sb.append(tableAlias).append(DOT).append(column);
            }
            if (useAlias) {
                sb.append(AS_CLAUSE).append(columnAlias);
            }
        }
        sb.append(COMMA);
    }

    /**
     * Returns escaped (quoted) column if escape needed.
     *
     * @param column the column
     * @param escape an indicator telling whether column needs to be escaped (quoted)
     * @return escaped (quoted) column if instructed to do so, otherwise original column value
     */
    private String escapeColumnIfNeeded(String column, boolean escape) {
        if (escape) {
            return quote(column);
        }
        return column;
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

    private void buildSelectClause(AnnotationMetadata annotationMetadata, QueryModel query, QueryState queryState, StringBuilder queryString) {
        String logicalName = queryState.getRootAlias();
        PersistentEntity entity = queryState.getEntity();
        buildSelect(
            annotationMetadata,
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
     *
     * @param annotationMetadata the annotation metadata
     * @param queryState the query state
     * @param queryString the query string builder
     * @param projectionList projection list (can be empty, then selects all columns)
     * @param tableAlias the table alias
     * @param entity the persistent entity
     */
    protected void buildSelect(AnnotationMetadata annotationMetadata,
                               QueryState queryState,
                               StringBuilder queryString,
                               List<QueryModel.Projection> projectionList,
                               String tableAlias,
                               PersistentEntity entity) {
        if (projectionList.isEmpty()) {
            selectAllColumns(annotationMetadata, queryState, queryString);
        } else {
            int projectionCount = projectionList.size();
            for (Iterator<QueryModel.Projection> i = projectionList.iterator(); i.hasNext(); ) {
                boolean appendComma = true;
                boolean removeComma = false;
                QueryModel.Projection projection = i.next();
                if (projection instanceof QueryModel.RootEntityProjection) {
                    selectAllColumns(annotationMetadata, queryState, queryString);
                    return;
                }
                if (projection instanceof QueryModel.LiteralProjection literalProjection) {
                    queryString.append(asLiteral(literalProjection.getValue()));
                } else if (projection instanceof QueryModel.CountProjection) {
                    appendProjectionRowCount(queryString, tableAlias);
                } else if (projection instanceof QueryModel.DistinctProjection) {
                    queryString.append("DISTINCT ");
                    if (projectionCount == 1) {
                        queryString.append(tableAlias)
                            .append(DOT)
                            .append("*");
                    }
                    appendComma = false;
                } else if (projection instanceof QueryModel.CountDistinctRootProjection) {
                    appendProjectionRowCountDistinct(queryString, queryState, entity, annotationMetadata, tableAlias);
                } else if (projection instanceof QueryModel.IdProjection) {
                    if (entity.hasCompositeIdentity()) {
                        for (PersistentProperty identity : entity.getCompositeIdentity()) {
                            appendPropertyProjection(annotationMetadata, entity, queryString, asQueryPropertyPath(queryState.getRootAlias(), identity), null);
                            queryString.append(COMMA);
                        }
                        queryString.setLength(queryString.length() - 1);
                    } else if (entity.hasIdentity()) {
                        List<PersistentProperty> identityProperties = entity.getIdentityProperties();
                        if (identityProperties.isEmpty()) {
                            throw new IllegalArgumentException(CANNOT_QUERY_ON_ID_WITH_ENTITY_THAT_HAS_NO_ID);
                        }
                        for (PersistentProperty identity : identityProperties) {
                            appendPropertyProjection(annotationMetadata, queryState.getEntity(), queryString, asQueryPropertyPath(queryState.getRootAlias(), identity), null);
                        }
                    } else {
                        throw new IllegalArgumentException(CANNOT_QUERY_ON_ID_WITH_ENTITY_THAT_HAS_NO_ID);
                    }
                } else if (projection instanceof QueryModel.PropertyProjection pp) {
                    String alias = pp.getAlias().orElse(null);
                    if (projection instanceof QueryModel.AvgProjection) {
                        appendFunctionProjection(annotationMetadata, queryState.getEntity(), AVG, pp, tableAlias, alias, queryString);
                    } else if (projection instanceof QueryModel.SumProjection) {
                        appendFunctionProjection(annotationMetadata, queryState.getEntity(), SUM, pp, tableAlias, alias, queryString);
                    } else if (projection instanceof QueryModel.MinProjection) {
                        appendFunctionProjection(annotationMetadata, queryState.getEntity(), MIN, pp, tableAlias, alias, queryString);
                    } else if (projection instanceof QueryModel.MaxProjection) {
                        appendFunctionProjection(annotationMetadata, queryState.getEntity(), MAX, pp, tableAlias, alias, queryString);
                    } else if (projection instanceof QueryModel.CountDistinctProjection) {
                        appendFunctionProjection(annotationMetadata, queryState.getEntity(), COUNT_DISTINCT, pp, tableAlias, alias, queryString);
                        queryString.append(CLOSE_BRACKET);
                    } else {
                        String propertyName = pp.getPropertyName();
                        PersistentPropertyPath propertyPath = entity.getPropertyPath(propertyName);
                        if (propertyPath == null) {
                            throw new IllegalArgumentException("Cannot project on non-existent property: " + propertyName);
                        }
                        PersistentProperty property = propertyPath.getProperty();
                        if (pp.isCompound()) {
                            // Compound property which is part of a DTO
                            if (property instanceof Association association && !(property instanceof Embedded)) {
                                if (queryState.isJoined(propertyPath.getPath())) {
                                    appendCompoundAssociationProjection(queryState, queryString, association, propertyPath, alias);
                                } else {
                                    removeComma = true;
                                }
                            } else {
                                appendCompoundPropertyProjection(queryState, queryString, property, propertyPath, alias);
                            }
                        } else {
                            if (property instanceof Association && !(property instanceof Embedded)) {
                                if (!appendAssociationProjection(queryState, queryString, property, propertyPath, alias)) {
                                    continue;
                                }
                            } else {
                                appendPropertyProjection(annotationMetadata, queryState.getEntity(), queryString, findProperty(queryState, propertyName, null), alias);
                            }
                        }
                    }
                }
                if (removeComma) {
                    queryString.setLength(queryString.length() - 1);
                }
                if (appendComma && i.hasNext()) {
                    queryString.append(COMMA);
                }
            }
        }
    }

    /**
     * Appends the compound (part of entity or DTO) property projection.
     *
     * @param queryState   The query state
     * @param queryString  The builder
     * @param property     The property
     * @param propertyPath The property path
     * @param columnAlias  The column alias
     */
    @Internal
    protected void appendCompoundPropertyProjection(QueryState queryState, StringBuilder queryString, PersistentProperty property, PersistentPropertyPath propertyPath, String columnAlias) {
        PersistentEntity entity = property.getOwner();
        boolean escape = shouldEscape(entity);
        NamingStrategy namingStrategy = getNamingStrategy(entity);
        int[] propertiesCount = new int[1];
        traversePersistentProperties(propertyPath.getAssociations(), property, (associations, p) -> {
            appendProperty(queryString, associations, p, namingStrategy, queryState.rootAlias, escape);
            propertiesCount[0]++;
        });
        queryString.setLength(queryString.length() - 1);
        if (StringUtils.isNotEmpty(columnAlias)) {
            if (propertiesCount[0] > 1) {
                throw new IllegalStateException("Cannot apply a column alias: " + columnAlias + " with expanded property: " + propertyPath);
            }
            if (propertiesCount[0] == 1) {
                queryString.append(AS_CLAUSE).append(columnAlias);
            }
        }
    }

    /**
     * Appends the compound (part of entity or DTO) association projection.
     *
     * @param queryState   The query state
     * @param queryString  The builder
     * @param association  The association
     * @param propertyPath The property path
     * @param columnAlias  The column alias
     */
    @Internal
    protected void appendCompoundAssociationProjection(QueryState queryState, StringBuilder queryString, Association association, PersistentPropertyPath propertyPath, String columnAlias) {
        if (!queryString.isEmpty() && queryString.charAt(queryString.length() - 1) == ',') {
            // Strip last .
            queryString.setLength(queryString.length() - 1);
        }
        selectAllColumnsFromJoinPaths(queryState, queryString, queryState.getQueryModel().getJoinPaths(), null);
    }

    protected final void appendPropertyProjection(AnnotationMetadata annotationMetadata, PersistentEntity entity, StringBuilder sb, QueryPropertyPath propertyPath, String columnAlias) {
        boolean jsonEntity = isJsonEntity(annotationMetadata, entity);
        if (!computePropertyPaths() || jsonEntity) {
            sb.append(propertyPath.getTableAlias()).append(DOT);
            String jsonEntityColumn = null;
            if (jsonEntity) {
                jsonEntityColumn = getJsonEntityColumn(annotationMetadata);
                if (jsonEntityColumn != null) {
                    checkDialectSupportsJsonEntity(entity);
                }
                sb.append(jsonEntityColumn).append(DOT);
            }
            sb.append(propertyPath.getPath());
            if (jsonEntityColumn != null) {
                appendJsonProjection(sb, propertyPath.getProperty().getDataType());
            }
            return;
        }
        String tableAlias = propertyPath.getTableAlias();
        boolean escape = propertyPath.shouldEscape();
        NamingStrategy namingStrategy = propertyPath.getNamingStrategy();
        boolean[] needsTrimming = {false};
        int[] propertiesCount = new int[1];

        traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), (associations, property) -> {
            appendProperty(sb, associations, property, namingStrategy, tableAlias, escape);
            needsTrimming[0] = true;
            propertiesCount[0]++;
        });
        if (needsTrimming[0]) {
            sb.setLength(sb.length() - 1);
        }
        if (StringUtils.isNotEmpty(columnAlias)) {
            if (propertiesCount[0] > 1) {
                throw new IllegalStateException("Cannot apply a column alias: " + columnAlias + " with expanded property: " + propertyPath);
            }
            if (propertiesCount[0] == 1) {
                sb.append(AS_CLAUSE).append(columnAlias);
            }
        }
    }

    /**
     * Appends selection projection for the property which is association.
     *
     * @param queryState the query state
     * @param queryString the query string builder
     * @param property the persistent property
     * @param propertyPath the persistent property path
     * @param columnAlias the column alias
     * @return true if association projection is appended, otherwise false
     */
    protected boolean appendAssociationProjection(QueryState queryState, StringBuilder queryString, PersistentProperty property, PersistentPropertyPath propertyPath, String columnAlias) {
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
        AnnotationMetadata annotationMetadata,
        PersistentEntity entity,
        String functionName,
        QueryModel.PropertyProjection propertyProjection,
        String tableAlias,
        String columnAlias,
        StringBuilder queryString) {
        PersistentPropertyPath propertyPath = entity.getPropertyPath(propertyProjection.getPropertyName());
        if (propertyPath == null) {
            throw new IllegalArgumentException("Cannot project on non-existent property: " + propertyProjection.getPropertyName());
        }
        boolean jsonEntity = isJsonEntity(annotationMetadata, entity);
        String columnName;
        if (computePropertyPaths() && !jsonEntity) {
            columnName = getMappedName(getNamingStrategy(entity), propertyPath.getAssociations(), propertyPath.getProperty());
            if (shouldEscape(entity)) {
                columnName = quote(columnName);
            }
        } else {
            columnName = propertyPath.getPath();
        }
        String jsonEntityColumn = getJsonEntityColumn(annotationMetadata);
        queryString.append(functionName)
            .append(OPEN_BRACKET)
            .append(tableAlias)
            .append(DOT);
        if (jsonEntityColumn != null) {
            queryString.append(jsonEntityColumn).append(DOT);
        }
        queryString.append(columnName);
        if (jsonEntityColumn != null) {
            DataType dataType = propertyPath.getProperty().getDataType();
            appendJsonProjection(queryString, dataType);
        }
        queryString.append(CLOSE_BRACKET);
        if (columnAlias != null) {
            queryString.append(AS_CLAUSE).append(columnAlias);
        }
    }

    /**
     * Appends value projection for JSON View field.
     *
     * @param sb the string builder
     * @param dataType the property data type
     */
    private void appendJsonProjection(StringBuilder sb, DataType dataType) {
        if (dataType == DataType.STRING) {
            sb.append(".string()");
        } else if (dataType.isNumeric() || dataType == DataType.BOOLEAN) {
            // Boolean is represented as number in Oracle (which only supports json view)
            sb.append(".number()");
        } else if (dataType == DataType.TIMESTAMP) {
            sb.append(".timestamp()");
        } else if (dataType == DataType.DATE) {
            sb.append(".date()");
        }
    }

    /**
     * Appends a row count projection to the query string.
     *
     * @param queryString The query string
     * @param logicalName The alias to the table name
     */
    protected abstract void appendProjectionRowCount(StringBuilder queryString, String logicalName);

    /**
     * Appends a row count distinct projection to the query string.
     *
     * @param queryString The query string
     * @param queryState The query state
     * @param entity The persistent entity
     * @param annotationMetadata The query annotation metadata
     * @param logicalName The alias to the table name
     */
    protected abstract void appendProjectionRowCountDistinct(StringBuilder queryString, QueryState queryState,
                                                             PersistentEntity entity, AnnotationMetadata annotationMetadata,
                                                             String logicalName);

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

            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return ctx.getAnnotationMetadata();
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

                @Override
                public AnnotationMetadata getAnnotationMetadata() {
                    return annotationMetadata;
                }
            };

            queryClause.append(OPEN_BRACKET);
            handleJunction(ctx, criteria);

            String additionalWhere = buildAdditionalWhereClause(queryState, annotationMetadata);
            appendAdditionalWhere(queryClause, queryState, additionalWhere);

        } else {
            String additionalWhere = buildAdditionalWhereClause(queryState, annotationMetadata);
            if (StringUtils.isNotEmpty(additionalWhere)) {
                queryClause.append(WHERE_CLAUSE)
                    .append(OPEN_BRACKET);
                appendAdditionalWhere(queryClause, queryState, additionalWhere);
            }
        }
    }

    /**
     * Builds additional where clause if there is {@link Where} annotation on the entity.
     *
     * @param queryState the query state
     * @param annotationMetadata the annotation metadata
     * @return where clause if there was {@link Where} annotation on the entity (or joins for JPA implementation)
     */
    protected String buildAdditionalWhereClause(QueryState queryState, AnnotationMetadata annotationMetadata) {
        return buildAdditionalWhereString(queryState.getRootAlias(), queryState.getEntity(), annotationMetadata);
    }

    private void appendAdditionalWhere(StringBuilder queryClause, QueryState queryState, String additionalWhere) {
        String queryStr = queryClause.toString();
        if (StringUtils.isNotEmpty(additionalWhere)) {
            StringBuffer additionalWhereBuilder = new StringBuffer();
            Matcher matcher = QueryBuilder.VARIABLE_PATTERN.matcher(additionalWhere);
            while (matcher.find()) {
                String name = matcher.group(3);
                String placeholder = queryState.addAdditionalRequiredParameter(name);
                //appendReplacement considers $ to be a special character, need to escape it.
                matcher.appendReplacement(additionalWhereBuilder, Matcher.quoteReplacement(placeholder));
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

    /**
     * Builds WHERE clause for the entity and given alias if {@link IgnoreWhere} is not present.
     *
     * @param alias the entity alias
     * @param entity the entity
     * @param annotationMetadata the entity metadata
     * @return the WHERE clause
     */
    protected String buildAdditionalWhereString(String alias, PersistentEntity entity, AnnotationMetadata annotationMetadata) {
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

    /**
     * Builds WHERE clause based on {@link Where} annotation on the metadata.
     *
     * @param joinPath the join path
     * @param annotationMetadata the annotation metadata
     * @return WHERE clause if {@link Where} annotation is declared and {@link IgnoreWhere} is not present, otherwise empty string
     */
    protected final String buildAdditionalWhereString(JoinPath joinPath, AnnotationMetadata annotationMetadata) {
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

    /**
     * Resolves where clause if there is {@link Where} annotation on the entity.
     * @param alias the entity alias
     * @param annotationMetadata the entity annotation metadata
     * @return where clause with entity alias if entity has declared where annotation
     */
    protected final String resolveWhereForAnnotationMetadata(String alias, AnnotationMetadata annotationMetadata) {
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
     * @param annotationMetadata the annotation metadata
     * @param query the query model
     * @param queryState the query state
     */
    protected void appendOrder(AnnotationMetadata annotationMetadata, QueryModel query, QueryState queryState) {
        List<Sort.Order> orders = query.getSort().getOrderBy();
        if (!orders.isEmpty()) {
            StringBuilder buff = queryState.getQuery();
            buff.append(ORDER_BY_CLAUSE);
            Iterator<Sort.Order> i = orders.iterator();

            String jsonEntityColumn = getJsonEntityColumn(annotationMetadata);

            while (i.hasNext()) {
                Sort.Order order = i.next();
                QueryPropertyPath propertyPath = findProperty(queryState, order.getProperty(), Sort.Order.class);
                String currentAlias = propertyPath.getTableAlias();
                if (currentAlias != null) {
                    buff.append(currentAlias).append(DOT);
                }
                if (jsonEntityColumn != null) {
                    buff.append(jsonEntityColumn).append(DOT);
                }
                if (computePropertyPaths() && jsonEntityColumn == null) {
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
                                           AnnotationMetadata annotationMetadata,
                                           PersistentEntity persistentEntity,
                                           PropertyParameterCreator propertyParameterCreator,
                                           QueryPropertyPath propertyPath,
                                           Object value,
                                           String operator) {
        appendCriteriaForOperator(whereClause, annotationMetadata, persistentEntity, propertyParameterCreator, propertyPath.propertyPath, propertyPath, value, operator);
    }

    private void appendCriteriaForOperator(StringBuilder whereClause,
                                           AnnotationMetadata annotationMetadata,
                                           PersistentEntity persistentEntity,
                                           PropertyParameterCreator propertyParameterCreator,
                                           PersistentPropertyPath parameterPropertyPath,
                                           QueryPropertyPath propertyPath,
                                           Object value,
                                           String operator) {

        if (value instanceof BindingParameter bindingParameter) {
            boolean computePropertyPaths = computePropertyPaths();
            boolean jsonEntity = isJsonEntity(annotationMetadata, persistentEntity);
            if (!computePropertyPaths || jsonEntity) {
                appendPropertyRef(whereClause, annotationMetadata, persistentEntity, propertyPath);
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
                if (currentAlias != null) {
                    whereClause.append(currentAlias).append(DOT);
                }

                String columnName = getMappedName(namingStrategy, associations, property);
                if (shouldEscape) {
                    columnName = quote(columnName);
                }
                whereClause.append(columnName);

                whereClause.append(operator);
                String writeTransformer = getDataTransformerWriteValue(currentAlias, property).orElse(null);
                Runnable pushParameter = () -> {
                    propertyParameterCreator.pushParameter(
                        bindingParameter,
                        newBindingContext(parameterPropertyPath, PersistentPropertyPath.of(associations, property))
                    );
                };
                if (writeTransformer != null) {
                    appendTransformed(whereClause, writeTransformer, pushParameter);
                } else {
                    pushParameter.run();
                }
                whereClause.append(LOGICAL_AND);
                needsTrimming[0] = true;
            });

            if (needsTrimming[0]) {
                whereClause.setLength(whereClause.length() - LOGICAL_AND.length());
            }

        } else {
            appendPropertyRef(whereClause, annotationMetadata, persistentEntity, propertyPath);
            whereClause.append(operator).append(asLiteral(value));
        }
    }

    /**
     * Appends property to the sql string builder.
     *
     * @param sb                 the sql string builder
     * @param annotationMetadata the annotation metadata
     * @param entity             the persistent entity
     * @param propertyPath       the query property path
     */
    protected void appendPropertyRef(StringBuilder sb, AnnotationMetadata annotationMetadata, PersistentEntity entity, QueryPropertyPath propertyPath) {
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
        boolean jsonEntity = isJsonEntity(annotationMetadata, entity);
        if (computePropertyPaths && !jsonEntity) {
            sb.append(propertyPath.getColumnName());
        } else {
            if (jsonEntity) {
                String jsonEntityColumn = getJsonEntityColumn(annotationMetadata);
                if (jsonEntityColumn != null) {
                    sb.append(jsonEntityColumn).append(DOT);
                    PersistentProperty property = propertyPath.getProperty();
                    if (property == entity.getIdentity()) {
                        sb.append("\"" + property.getPersistedName() + "\"");
                    } else {
                        sb.append(propertyPath.getPath());
                    }
                }
            } else {
                sb.append(propertyPath.getPath());
            }
        }
    }

    private String getJsonEntityColumn(AnnotationMetadata annotationMetadata) {
        AnnotationValue<EntityRepresentation> entityRepresentationAnnotationValue = annotationMetadata.getAnnotation(EntityRepresentation.class);
        if (entityRepresentationAnnotationValue != null) {
            return entityRepresentationAnnotationValue.getRequiredValue(JSON_COLUMN, String.class);
        }
        return null;
    }

    private void appendCaseInsensitiveCriterion(CriteriaContext ctx,
                                                QueryModel.PropertyCriterion criterion,
                                                String operator) {
        QueryPropertyPath propertyPath = ctx.getRequiredProperty(criterion);
        StringBuilder whereClause = ctx.query();
        whereClause.append("LOWER(");
        appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
        whereClause.append(")")
            .append(operator)
            .append("LOWER(");
        appendPlaceholderOrLiteral(ctx, propertyPath, criterion.getValue());
        whereClause.append(")");
    }

    private void appendPlaceholderOrLiteral(CriteriaContext ctx, QueryPropertyPath propertyPath, Object value) {
        if (value instanceof BindingParameter bindingParameter) {
            ctx.pushParameter(bindingParameter, newBindingContext(propertyPath.propertyPath));
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
        appendPropertyRef(whereClause, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), propertyPath);
        whereClause.append(comparisonExpression);
        // TODO: support subqueryCriterion
        whereClause.append(CLOSE_BRACKET);
    }

    private void buildUpdateStatement(AnnotationMetadata annotationMetadata, QueryState queryState, Map<String, Object> propertiesToUpdate) {
        StringBuilder queryString = queryState.getQuery();
        queryString.append(SPACE).append("SET").append(SPACE);

        PersistentEntity entity = queryState.getEntity();
        boolean jsonEntity = isJsonEntity(annotationMetadata, queryState.getEntity());
        if (jsonEntity && propertiesToUpdate.size() == 1) {
            checkDialectSupportsJsonEntity(entity);
            // Update JsonView DATA column
            String name = propertiesToUpdate.keySet().iterator().next();
            String jsonEntityColumn = getJsonEntityColumn(annotationMetadata);
            if (name.equals(jsonEntityColumn)) {
                Object value = propertiesToUpdate.get(name);
                queryString.append(queryState.getRootAlias()).append(DOT).append(jsonEntityColumn).append("=");
                if (value instanceof BindingParameter) {
                    int key = 1;
                    queryState.pushParameter(new QueryParameterBinding() {

                        @Override
                        public String getName() {
                            return String.valueOf(key);
                        }

                        @Override
                        public String getKey() {
                            return String.valueOf(key);
                        }

                        @Override
                        public DataType getDataType() {
                            return DataType.JSON;
                        }

                        @Override
                        public JsonDataType getJsonDataType() {
                            return JsonDataType.DEFAULT;
                        }

                        @Override
                        public int getParameterIndex() {
                            return -1;
                        }
                    });
                } else {
                    queryString.append(asLiteral(value));
                }
                return;
            }
        }

        // keys need to be sorted before query is built
        List<Map.Entry<QueryPropertyPath, Object>> update = propertiesToUpdate.entrySet().stream()
            .map(e -> {
                QueryPropertyPath propertyPath = findProperty(queryState, e.getKey(), null);
                if (propertyPath.getProperty() instanceof Association association && association.isForeignKey()) {
                    throw new IllegalArgumentException("Foreign key associations cannot be updated as part of a batch update statement");
                }
                return new AbstractMap.SimpleEntry<>(propertyPath, e.getValue());
            })
            .filter(e -> !(e.getValue() instanceof QueryParameter) || !e.getKey().getProperty().isGenerated())
            .collect(Collectors.toList());

        boolean[] needsTrimming = {false};
        if (!computePropertyPaths() || jsonEntity) {
            String jsonViewColumnName = getJsonEntityColumn(annotationMetadata);
            if (jsonViewColumnName != null) {
                queryString.append(queryState.getRootAlias()).append(DOT).append(jsonViewColumnName).append("= json_transform(").append(jsonViewColumnName);
            }
            for (Map.Entry<QueryPropertyPath, Object> entry : update) {
                QueryPropertyPath propertyPath = entry.getKey();
                PersistentProperty prop = propertyPath.getProperty();
                String tableAlias = propertyPath.getTableAlias();
                if (jsonViewColumnName != null) {
                    queryString.append(", SET '$.").append(propertyPath.getPath()).append("' = ");
                } else {
                    if (tableAlias != null) {
                        queryString.append(tableAlias).append(DOT);
                    }
                    queryString.append(propertyPath.getPath()).append('=');
                }
                if (entry.getValue() instanceof BindingParameter bindingParameter) {
                    appendUpdateSetParameter(queryString, tableAlias, prop, () -> {
                        queryState.pushParameter(bindingParameter, newBindingContext(propertyPath.propertyPath));
                    });
                } else {
                    queryString.append(asLiteral(entry.getValue()));
                }
                if (jsonViewColumnName == null) {
                    queryString.append(COMMA);
                    needsTrimming[0] = true;
                }
            }
            if (jsonViewColumnName != null) {
                queryString.append(CLOSE_BRACKET);
            }
        } else {
            NamingStrategy namingStrategy = getNamingStrategy(queryState.getEntity());
            for (Map.Entry<QueryPropertyPath, Object> entry : update) {
                QueryPropertyPath propertyPath = entry.getKey();
                if (entry.getValue() instanceof BindingParameter bindingParameter) {
                    traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), (associations, property) -> {

                        boolean generated = PersistentEntityUtils.isPropertyGenerated(entity,
                            propertyPath.getProperty(), property);
                        if (generated) {
                            return;
                        }

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
                                bindingParameter,
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
        int parameterPosition = transformed.indexOf('?');
        if (parameterPosition > -1) {
            if (transformed.lastIndexOf('?') != parameterPosition) {
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
        appendPropertyRef(sb, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), ctx.getRequiredProperty(comparisonCriterion.getProperty(), comparisonCriterion.getClass()));
        sb.append(operator);
        appendPropertyRef(sb, ctx.getAnnotationMetadata(), ctx.getPersistentEntity(), ctx.getRequiredProperty(comparisonCriterion.getOtherProperty(), comparisonCriterion.getClass()));
    }

    @NonNull
    private QueryPropertyPath findProperty(QueryState queryState, String propertypath, Class criterionType) {
        return findPropertyInternal(queryState, queryState.getEntity(), queryState.getRootAlias(), propertypath, criterionType);
    }

    private QueryPropertyPath findPropertyInternal(QueryState queryState, PersistentEntity entity, String tableAlias, String propertypath, Class criterionType) {
        PersistentPropertyPath propertyPath = entity.getPropertyPath(propertypath);
        if (propertyPath != null) {
            if (propertyPath.getAssociations().isEmpty()) {
                return new QueryPropertyPath(propertyPath, tableAlias);
            }
            PersistentProperty property = propertyPath.getProperty();
            Association joinAssociation = null;
            StringJoiner joinPathJoiner = new StringJoiner(".");
            String lastJoinAlias = null;
            for (Association association : propertyPath.getAssociations()) {
                if (association instanceof Embedded) {
                    joinPathJoiner.add(association.getName());
                    continue;
                }
                if (joinAssociation == null) {
                    joinPathJoiner.add(association.getName());
                    joinAssociation = association;
                    continue;
                }
                if (PersistentEntityUtils.isAccessibleWithoutJoin(association, propertyPath.getProperty())) {
                    // We don't need to join to access the id of the relation
                    if (lastJoinAlias == null) {
                        String joinStringPath = joinPathJoiner.toString();
                        if (!queryState.isJoined(joinStringPath)) {
                            throw new IllegalArgumentException("Property is not joined at path: " + joinStringPath);
                        }
                        lastJoinAlias = joinInPath(queryState, joinPathJoiner.toString());
                    }
                    return new QueryPropertyPath(
                        new PersistentPropertyPath(Collections.emptyList(), association),
                        lastJoinAlias
                    );
                }

                joinPathJoiner.add(association.getName());
                if (!queryState.isAllowJoins()) {
                    throw new IllegalArgumentException("Joins cannot be used in a DELETE or UPDATE operation" + (association.getAssociatedEntity().getIdentity() == propertyPath.getProperty()));
                }
                String joinStringPath = joinPathJoiner.toString();
                if (!queryState.isJoined(joinStringPath)) {
                    throw new IllegalArgumentException("Property is not joined at path: " + joinStringPath);
                }
                lastJoinAlias = joinInPath(queryState, joinStringPath);
                // Continue to look for a joined property
                joinAssociation = association;
            }
            if (joinAssociation != null) {
                // We don't need to join to access the id of the relation if it is not a foreign key association
                if (!PersistentEntityUtils.isAccessibleWithoutJoin(joinAssociation, propertyPath.getProperty())) {
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
                        new PersistentPropertyPath(Collections.emptyList(), property),
                        lastJoinAlias
                    );
                }
            }
        } else if (TypeRole.ID.equals(propertypath) && entity.getIdentity() != null) {
            // special case handling for ID
            return new QueryPropertyPath(
                new PersistentPropertyPath(Collections.emptyList(), entity.getIdentity(), entity.getIdentity().getName()),
                queryState.getRootAlias()
            );
        }
        if (propertyPath == null) {
            if (criterionType == null || criterionType == Sort.Order.class) {
                throw new IllegalArgumentException("Cannot order on non-existent property path: " + propertypath);
            } else {
                throw new IllegalArgumentException("Cannot use [" + criterionType.getSimpleName() + "] criterion on non-existent property path: " + propertypath);
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
        QueryState queryState = newQueryState(query, false, isAliasForBatch(entity, annotationMetadata));
        StringBuilder queryString = queryState.getQuery();
        String tableAlias = queryState.getRootAlias();
        String tableName = getTableName(entity);
        queryString.append(UPDATE_CLAUSE).append(tableName);
        if (tableAlias != null) {
            queryString.append(SPACE).append(tableAlias);
        }
        buildUpdateStatement(annotationMetadata, queryState, propertiesToUpdate);
        buildWhereClause(annotationMetadata, query.getCriteria(), queryState);

        if (!query.getProjections().isEmpty()) {
            if (!getDialect().supportsUpdateReturning()) {
                throw new IllegalStateException("Dialect: " + getDialect() + " doesn't support UPDATE ... RETURNING clause");
            }
            queryString.append(RETURNING);
            buildSelect(
                annotationMetadata,
                queryState,
                queryString,
                query.getProjections(),
                tableAlias,
                entity
            );
        }
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
        QueryState queryState = newQueryState(query, false, isAliasForBatch(entity, annotationMetadata));
        StringBuilder queryString = queryState.getQuery();
        String tableAlias = queryState.getRootAlias();
        StringBuilder buffer = appendDeleteClause(queryString);
        String tableName = getTableName(entity);
        buffer.append(tableName).append(SPACE);
        if (tableAlias != null) {
            buffer.append(getTableAsKeyword()).append(tableAlias);
        }
        buildWhereClause(annotationMetadata, query.getCriteria(), queryState);
        if (!query.getProjections().isEmpty()) {
            if (!getDialect().supportsDeleteReturning()) {
                throw new IllegalStateException("Dialect: " + getDialect() + " doesn't support DELETE ... RETURNING clause");
            }
            queryString.append(RETURNING);
            buildSelect(
                annotationMetadata,
                queryState,
                queryString,
                query.getProjections(),
                tableAlias,
                entity
            );
        }
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
     * @param persistentEntity the persistent entity
     * @param annotationMetadata the method annotation metadata
     * @return True if they should
     */
    protected abstract boolean isAliasForBatch(PersistentEntity persistentEntity, AnnotationMetadata annotationMetadata);


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
        return buildOrderBy("", entity, AnnotationMetadata.EMPTY_METADATA, sort, false);
    }

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query  The query
     * @param entity The root entity
     * @param annotationMetadata The annotation metadata
     * @param sort   The sort
     * @return The encoded query
     *
     * @deprecated use {@link #buildOrderBy(String, PersistentEntity, AnnotationMetadata, Sort, boolean)}
     */
    @NonNull
    @Deprecated(forRemoval = true, since = "4.2.0")
    public QueryResult buildOrderBy(String query, @NonNull PersistentEntity entity, @NonNull AnnotationMetadata annotationMetadata, @NonNull Sort sort) {
        return buildOrderBy(query, entity, annotationMetadata, sort, false);
    }

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query  The query
     * @param entity The root entity
     * @param annotationMetadata The annotation metadata
     * @param sort   The sort
     * @param nativeQuery Whether the query is native query, in which case sort field names will be supplied by the user and not verified
     * @return The encoded query
     */
    @NonNull
    public QueryResult buildOrderBy(String query, @NonNull PersistentEntity entity, @NonNull AnnotationMetadata annotationMetadata, @NonNull Sort sort,
                                    boolean nativeQuery) {
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
            boolean ignoreCase = order.isIgnoreCase();
            if (ignoreCase) {
                buff.append("LOWER(");
            }
            buff.append(buildPropertyByName(property, query, entity, annotationMetadata, nativeQuery));
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
     * Encode the given property retrieval into a query instance.
     * For example, property name might be encoded as {@code `person_.name`} using
     * its path and table's alias.
     *
     * @param propertyName The name of the property
     * @param query The query
     * @param entity The root entity
     * @param annotationMetadata The annotation metadata
     * @param nativeQuery Whether the query is native query, in which case the property name will be supplied by the user and not verified
     * @return The encoded query
     */
    public String buildPropertyByName(
            @NonNull String propertyName, @NonNull String query,
            @NonNull PersistentEntity entity, @NonNull AnnotationMetadata annotationMetadata,
            boolean nativeQuery
    ) {
        if (nativeQuery) {
            return propertyName;
        }

        PersistentPropertyPath path = entity.getPropertyPath(propertyName);
        if (path == null) {
            throw new IllegalArgumentException("Cannot sort on non-existent property path: " + propertyName);
        }
        List<Association> associations = new ArrayList<>(path.getAssociations());
        int assocCount = associations.size();
        // If last association is embedded, it does not need to be joined to the alias since it will be in the destination table
        // JPA/Hibernate is special case and in that case we leave association for specific handling below
        if (assocCount > 0 && computePropertyPaths() && associations.get(assocCount - 1) instanceof Embedded) {
            associations.remove(assocCount - 1);
        }

        StringBuilder buff = new StringBuilder();
        if (associations.isEmpty()) {
            buff.append(getAliasName(entity));
        } else {
            StringJoiner joiner = new StringJoiner(".");
            for (Association association : associations) {
                joiner.add(association.getName());
            }
            String joinAlias = getAliasName(new JoinPath(joiner.toString(), associations.toArray(new Association[0]), Join.Type.DEFAULT, null));
            if (!computePropertyPaths()) {
                if (!query.contains(" " + joinAlias + " ") && !query.endsWith(" " + joinAlias)) {
                    // Special hack case for JPA, Hibernate can join the relation with cross join automatically when referenced by the property path
                    // This probably should be removed in the future major version
                    buff.append(getAliasName(entity)).append(DOT);
                    StringJoiner pathJoiner = new StringJoiner(".");
                    for (Association association : associations) {
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

        String jsonEntityColumn = getJsonEntityColumn(annotationMetadata);
        if (jsonEntityColumn != null) {
            buff.append(jsonEntityColumn).append(DOT);
        }

        if (!computePropertyPaths() || jsonEntityColumn != null) {
            buff.append(path.getProperty().getName());
        } else {
            buff.append(getColumnName(path.getProperty()));
        }

        return buff.toString();
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
     * @param property the persistent property
     * @return column alias if defined, otherwise an empty string
     */
    protected final String getColumnAlias(PersistentProperty property) {
        return property.getAlias();
    }

    /**
     * If and when {@link EntityRepresentation} annotation with JSON type is used for the repository method but dialect does not support
     * JSON entity representations this will throw {@link IllegalArgumentException}.
     *
     * @param entity the persistent entity
     */
    protected void checkDialectSupportsJsonEntity(PersistentEntity entity) {
        if (!getDialect().supportsJsonEntity()) {
            throw new IllegalArgumentException("Json representation for entity " + entity.getSimpleName() + " is not supported by the dialect " + getDialect());
        }
    }

    /**
     * Checks whether {@link EntityRepresentation} annotation with JSON type is used for the repository method.
     * If current dialect does not support handling JSON entity representations, {@link IllegalArgumentException} is thrown.
     *
     * @param annotationMetadata the annotation metadata
     * @param entity the persistent entity
     * @return true if {@link EntityRepresentation} annotation with JSON type is used for the repository method
     */
    protected boolean isJsonEntity(AnnotationMetadata annotationMetadata, PersistentEntity entity) {
        boolean jsonEntity = DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(annotationMetadata);
        if (jsonEntity) {
            checkDialectSupportsJsonEntity(entity);
        }
        return jsonEntity;
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

        @Override
        default void pushParameter(@NonNull BindingParameter bindingParameter, @NonNull BindingParameter.BindingContext bindingContext) {
            getQueryState().pushParameter(bindingParameter, bindingContext);
        }

        default QueryPropertyPath getRequiredProperty(QueryModel.PropertyNameCriterion propertyCriterion) {
            return getRequiredProperty(propertyCriterion.getProperty(), propertyCriterion.getClass());
        }

        default StringBuilder query() {
            return getQueryState().getQuery();
        }

        AnnotationMetadata getAnnotationMetadata();
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
        public @NonNull Map<String, String> getAdditionalRequiredParameters() {
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
        public void pushParameter(@NonNull BindingParameter bindingParameter, @NonNull BindingParameter.BindingContext bindingContext) {
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

        /**
         * Adds query parameter binding.
         *
         * @param parameterBinding the query parameter binding
         */
        public void pushParameter(@NonNull QueryParameterBinding parameterBinding) {
            parameterBindings.add(parameterBinding);
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

        void pushParameter(@NonNull BindingParameter bindingParameter,
                           @NonNull BindingParameter.BindingContext bindingContext);

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
         * @param name The name of the placeholder
         * @param key  The key to set the value of the placeholder
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
         * @return The placeholder name
         */
        public String getName() {
            return name;
        }

        /**
         * This the precomputed key to set the placeholder. In SQL this would be the index.
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
        public QueryPropertyPath(@NonNull PersistentPropertyPath propertyPath, @Nullable String tableAlias) {
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
