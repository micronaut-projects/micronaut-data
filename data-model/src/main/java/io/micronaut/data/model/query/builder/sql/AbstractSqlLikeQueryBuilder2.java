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
package io.micronaut.data.model.query.builder.sql;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
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
import io.micronaut.data.model.PersistentAssociationPath;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.ISelection;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityQuery;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.jpa.criteria.impl.DefaultPersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.IParameterExpression;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.FunctionExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.SubqueryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.BinaryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.PredicateBinaryOp;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.builder.QueryBuilder2;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.impl.AdvancedPredicateVisitor;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;

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
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.requireProperty;
import static io.micronaut.data.model.query.builder.QueryBuilder.VARIABLE_PATTERN;

/**
 * An abstract class for builders that build SQL-like queries.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
@Internal
@SuppressWarnings("FileLength")
public abstract class AbstractSqlLikeQueryBuilder2 implements QueryBuilder2 {
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
    protected static final String RETURNING = " RETURNING ";
    protected static final String OR = "OR";
    protected static final String LOGICAL_OR = " " + OR + " ";
    protected static final String DISTINCT = "DISTINCT ";
    protected static final String ALIAS_REPLACE_QUOTED = "@\\.";
    protected static final String CANNOT_QUERY_ON_ID_WITH_ENTITY_THAT_HAS_NO_ID = "Cannot query on ID with entity that has no ID";

    private static final String UNSUPPORTED_EXPRESSION = "Unsupported expression: ";

    /**
     * Get dialect.
     *
     * @return dialect
     */
    protected Dialect getDialect() {
        return Dialect.ANSI;
    }

    /**
     * @return True if embedded properties should be traversed
     */
    protected boolean traverseEmbedded() {
        return true;
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
        if (value instanceof Expression<?> expression) {
            throw new IllegalArgumentException(UNSUPPORTED_EXPRESSION + expression);
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

    protected final QueryPropertyPath asQueryPropertyPath(String tableAlias, PersistentProperty persistentProperty) {
        return new QueryPropertyPath(asPersistentPropertyPath(persistentProperty), tableAlias);
    }

    private PersistentPropertyPath asPersistentPropertyPath(PersistentProperty persistentProperty) {
        return PersistentPropertyPath.of(Collections.emptyList(), persistentProperty, persistentProperty.getName());
    }

    @Override
    public QueryResult buildSelect(AnnotationMetadata annotationMetadata, SelectQueryDefinition definition) {
        QueryBuilder queryBuilder = new QueryBuilder();
        QueryState queryState = buildQuery(annotationMetadata, definition, queryBuilder, false, null);

        return QueryResult.of(
            queryState.getFinalQuery(),
            queryState.getQueryParts(),
            queryState.getParameterBindings(),
            definition.limit(),
            definition.offset(),
            queryState.getJoinPaths()
        );
    }

    @NonNull
    protected final QueryState buildQuery(AnnotationMetadata annotationMetadata,
                                    SelectQueryDefinition definition,
                                    QueryBuilder queryBuilder,
                                    boolean supportsQueryPagination,
                                    @Nullable String tableAliasPrefix) {
        QueryState queryState = new QueryState(queryBuilder, definition, true, true, tableAliasPrefix);

        Predicate predicate = definition.predicate();
        Selection<?> selection = definition.selection();
        Objects.requireNonNull(selection, "Select query selection must not be null");

        List<JoinPath> joinPaths = new ArrayList<>(definition.getJoinPaths());
        joinPaths.sort((o1, o2) -> Comparator.comparingInt(String::length).thenComparing(String::compareTo).compare(o1.getPath(), o2.getPath()));
        for (JoinPath joinPath : joinPaths) {
            queryState.applyJoin(joinPath);
        }

        StringBuilder query = queryState.getQuery();
        query.append(SELECT_CLAUSE);
        buildSelectClause(annotationMetadata, definition, queryState);
        appendForUpdate(QueryPosition.AFTER_TABLE_NAME, definition, query);

        queryState.generateJoinQuery();

        if (predicate != null || annotationMetadata.hasStereotype(WhereSpecifications.class) || queryState.getEntity().getAnnotationMetadata().hasStereotype(WhereSpecifications.class)) {
            buildWhereClause(annotationMetadata, predicate, queryState);
        }
        appendPaginationAndOrder(annotationMetadata, definition, supportsQueryPagination, queryState);
        appendForUpdate(QueryPosition.END_OF_QUERY, definition, queryState.getQuery());
        return queryState;
    }

    protected void appendPaginationAndOrder(AnnotationMetadata annotationMetadata,
                                            SelectQueryDefinition definition,
                                            boolean pagination,
                                            QueryState queryState) {
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
     * @param joinType         The join type
     * @param query            The query builder
     * @param queryState       The state
     * @param joinAssociation  The association
     * @param associationOwner The associated owner
     * @param currentJoinAlias The current join alias
     * @param lastJoinAlias    The last join alias
     */
    protected void buildJoin(String joinType,
                             StringBuilder query,
                             QueryState queryState,
                             PersistentAssociationPath joinAssociation,
                             PersistentEntity associationOwner,
                             String currentJoinAlias,
                             String lastJoinAlias) {
    }

    /**
     * Get the column name for the given property.
     *
     * @param persistentProperty The property
     * @return The column name
     */
    protected abstract String getColumnName(PersistentProperty persistentProperty);

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

    private void buildSelectClause(AnnotationMetadata annotationMetadata,
                                   SelectQueryDefinition definition,
                                   QueryState queryState) {
        buildSelect(
            annotationMetadata,
            queryState,
            definition.selection(),
            definition.isDistinct()
        );

        queryState.getQuery().append(FROM_CLAUSE)
            .append(getTableName(queryState.getEntity()))
            .append(getTableAsKeyword())
            .append(queryState.getRootAlias());
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
     * @param queryState         the query state
     * @param selection          projection list (can be empty, then selects all columns)
     * @param distinct           is distinct selection
     */
    protected void buildSelect(AnnotationMetadata annotationMetadata,
                               QueryState queryState,
                               Selection<?> selection,
                               boolean distinct) {

        if (selection instanceof ISelection<?> selectionVisitable) {
            selectionVisitable.visitSelection(createSelectionVisitor(annotationMetadata, queryState, distinct));
        } else {
            throw new IllegalStateException("Unknown selection type: " + selection.getClass().getName());
        }
    }

    /**
     * Create a selection visitor.
     *
     * @param annotationMetadata The annotation metadata
     * @param queryState         The query state
     * @param distinct           The distinct
     * @return The visitor
     */
    protected SqlSelectionVisitor createSelectionVisitor(AnnotationMetadata annotationMetadata,
                                                         QueryState queryState,
                                                         boolean distinct) {
        return new SqlSelectionVisitor(queryState, annotationMetadata, distinct);
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
     * Gets the mapped name from the association using {@link NamingStrategy}.
     *
     * @param namingStrategy the naming strategy being used
     * @param association    the association
     * @return the mapped name for the association
     */
    @NonNull
    protected String getMappedName(@NonNull NamingStrategy namingStrategy, @NonNull Association association) {
        return namingStrategy.mappedName(association);
    }

    /**
     * Gets the mapped name from for the list of associations and property using {@link NamingStrategy}.
     *
     * @param namingStrategy the naming strategy
     * @param associations   the association list
     * @param property       the property
     * @return the mappen name for the list of associations and property using given naming strategy
     */
    @NonNull
    protected String getMappedName(@NonNull NamingStrategy namingStrategy, @NonNull List<Association> associations, @NonNull PersistentProperty property) {
        return namingStrategy.mappedName(associations, property);
    }

    /**
     * Gets the mapped name from for the list of associations and property using {@link NamingStrategy}.
     *
     * @param namingStrategy the naming strategy
     * @param propertyPath   the property path
     * @return the mappen name for the list of associations and property using given naming strategy
     */
    @NonNull
    protected String getMappedName(@NonNull NamingStrategy namingStrategy, @NonNull PersistentPropertyPath propertyPath) {
        return namingStrategy.mappedName(propertyPath.getAssociations(), propertyPath.getProperty());
    }

    /**
     * Builds where clause.
     *
     * @param annotationMetadata the annotation metadata for the method
     * @param predicate          the predicate
     * @param queryState         the query state
     */
    protected void buildWhereClause(AnnotationMetadata annotationMetadata, Predicate predicate, QueryState queryState) {
        String additionalWhere = buildAdditionalWhereClause(queryState, annotationMetadata);
        RenderablePredicate additionalWherePredicate = findAdditionalPredicate(additionalWhere);
        if (additionalWherePredicate != null) {
            if (predicate == null) {
                predicate = new ConjunctionPredicate(List.of(additionalWherePredicate));
            } else {
                predicate = new ConjunctionPredicate((Collection) List.of(predicate, additionalWherePredicate));
            }
        }
        if (predicate != null) {
            queryState.getQuery().append(WHERE_CLAUSE);
            if (predicate instanceof IPredicate predicateVisitable) {
                predicateVisitable.visitPredicate(createPredicateVisitor(annotationMetadata, queryState));
            } else {
                throw new IllegalStateException("Unsupported predicate type: " + predicate.getClass().getName());
            }
        }
    }

    /**
     * Create a predicate visitor.
     *
     * @param annotationMetadata The annotation metadata
     * @param queryState         The query state
     * @return The visitor
     */
    protected SqlPredicateVisitor createPredicateVisitor(AnnotationMetadata annotationMetadata, QueryState queryState) {
        return new SqlPredicateVisitor(queryState, annotationMetadata);
    }

    /**
     * Builds additional where clause if there is {@link Where} annotation on the entity.
     *
     * @param queryState         the query state
     * @param annotationMetadata the annotation metadata
     * @return where clause if there was {@link Where} annotation on the entity (or joins for JPA implementation)
     */
    protected String buildAdditionalWhereClause(QueryState queryState, AnnotationMetadata annotationMetadata) {
        return buildAdditionalWhereString(queryState.getRootAlias(), queryState.getEntity(), annotationMetadata);
    }

    @Nullable
    private RenderablePredicate findAdditionalPredicate(String additionalWhere) {
        if (StringUtils.isEmpty(additionalWhere)) {
            return null;
        }
        return new RenderablePredicate() {

            @Override
            void render(StringBuilder query, PropertyParameterCreator propertyParameterCreator) {
                Matcher matcher = VARIABLE_PATTERN.matcher(additionalWhere);
                int index = 0;
                while (matcher.find()) {
                    query.append(additionalWhere, index, matcher.start(2));
                    index = matcher.end(2);
                    String name = matcher.group(3);
                    propertyParameterCreator.pushAdditionalParameter(name);
                }
                if (index < additionalWhere.length()) {
                    query.append(additionalWhere, index, additionalWhere.length());
                }
            }
        };

    }

    /**
     * Builds WHERE clause for the entity and given alias if {@link IgnoreWhere} is not present.
     *
     * @param alias              the entity alias
     * @param entity             the entity
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
     * @param joinPath           the join path
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
     *
     * @param alias              the entity alias
     * @param annotationMetadata the entity annotation metadata
     * @return where clause with entity alias if entity has declared where annotation
     */
    protected final String resolveWhereForAnnotationMetadata(String alias, AnnotationMetadata annotationMetadata) {
        return annotationMetadata.getAnnotationValuesByType(Where.class)
            .stream()
            .flatMap(av -> av.stringValue().stream())
            .map(val -> replaceAlias(alias, val))
            .filter(StringUtils::isNotEmpty)
            .collect(Collectors.joining(LOGICAL_AND));
    }

    /**
     * Appends order to the query.
     *
     * @param annotationMetadata the annotation metadata
     * @param orders             the orders
     * @param queryState         the query state
     */
    protected void appendOrder(AnnotationMetadata annotationMetadata, List<Order> orders, QueryState queryState) {
        if (!orders.isEmpty()) {
            StringBuilder buff = queryState.getQuery();
            buff.append(ORDER_BY_CLAUSE);

            String jsonEntityColumn = getJsonEntityColumn(annotationMetadata);

            Iterator<Order> i = orders.iterator();
            while (i.hasNext()) {
                Order order = i.next();
                QueryPropertyPath propertyPath = queryState.findProperty(requireProperty(order.getExpression()).getPropertyPath());
                String currentAlias = propertyPath.getTableAlias();
                if (currentAlias != null) {
                    buff.append(currentAlias).append(DOT);
                }
                if (jsonEntityColumn != null) {
                    buff.append(jsonEntityColumn).append(DOT);
                }
                String direction;
                if (order.isAscending()) {
                    direction = "ASC";
                } else {
                    direction = "DESC";
                }
                if (computePropertyPaths() && jsonEntityColumn == null) {
                    buff.append(propertyPath.getColumnName()).append(SPACE).append(direction);
                } else {
                    buff.append(propertyPath.getPath());
                    if (jsonEntityColumn != null) {
                        appendJsonProjection(buff, propertyPath.getProperty().getDataType());
                    }
                    buff.append(SPACE).append(direction);
                }
                if (i.hasNext()) {
                    buff.append(",");
                }
            }
        }
    }

    /**
     * Adds "forUpdate" pessimistic locking.
     *
     * @param queryPosition The query position
     * @param definition    The definition
     * @param queryBuilder  The builder
     */
    protected void appendForUpdate(QueryPosition queryPosition, SelectQueryDefinition definition, StringBuilder queryBuilder) {
        if (definition.isForUpdate()) {
            throw new IllegalStateException("For update not supported for current query builder: " + getClass().getSimpleName());
        }
    }

    private String getJsonEntityColumn(AnnotationMetadata annotationMetadata) {
        AnnotationValue<EntityRepresentation> entityRepresentationAnnotationValue = annotationMetadata.getAnnotation(EntityRepresentation.class);
        if (entityRepresentationAnnotationValue != null) {
            return entityRepresentationAnnotationValue.getRequiredValue("column", String.class);
        }
        return null;
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
                QueryPropertyPath propertyPath = queryState.findProperty(e.getKey());
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
                    PersistentEntityUtils.traversePersistentProperties(propertyPath.getPropertyPath(), traverseEmbedded(), (associations, property) -> {
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
                                    PersistentPropertyPath.of(associations, property)
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

    /**
     * Whether property path expressions require computation by the implementation. In a certain query dialects
     * property paths are supported (such as JPA-QL where you can do select foo.bar) whilst for explicit SQL queries paths like
     * this have to be computed into aliases / column name references.
     *
     * @return True if property path computation is required.
     */
    protected abstract boolean computePropertyPaths();

    @Override
    public QueryResult buildUpdate(@NonNull AnnotationMetadata annotationMetadata, @NonNull UpdateQueryDefinition definition) {
        Map<String, Object> propertiesToUpdate = definition.propertiesToUpdate();
        if (propertiesToUpdate.isEmpty()) {
            throw new IllegalArgumentException("No properties specified to update");
        }
        boolean useAlias = isAliasForBatch(definition.persistentEntity(), annotationMetadata);
        QueryState queryState = new QueryState(definition, false, useAlias);
        StringBuilder queryString = queryState.getQuery();
        String tableAlias = queryState.getRootAlias();
        String tableName = getTableName(definition.persistentEntity());
        queryString.append("UPDATE ").append(tableName);
        if (tableAlias != null) {
            queryString.append(SPACE).append(tableAlias);
        }
        buildUpdateStatement(annotationMetadata, queryState, propertiesToUpdate);
        buildWhereClause(annotationMetadata, definition.predicate(), queryState);

        Selection<?> returningSelection = definition.returningSelection();
        if (returningSelection != null) {
            if (!getDialect().supportsUpdateReturning()) {
                throw new IllegalStateException("Dialect: " + getDialect() + " doesn't support UPDATE ... RETURNING clause");
            }
            queryString.append(RETURNING);
            buildSelect(
                annotationMetadata,
                queryState,
                returningSelection,
                false
            );
        }
        return QueryResult.of(
            queryState.getFinalQuery(),
            queryState.getQueryParts(),
            queryState.getParameterBindings()
        );
    }

    @Override
    public QueryResult buildDelete(@NonNull AnnotationMetadata annotationMetadata, @NonNull DeleteQueryDefinition definition) {
        boolean useAlias = isAliasForBatch(definition.persistentEntity(), annotationMetadata);
        QueryState queryState = new QueryState(definition, false, useAlias);
        StringBuilder queryString = queryState.getQuery();
        String tableAlias = queryState.getRootAlias();
        StringBuilder query = appendDeleteClause(queryString);
        String tableName = getTableName(definition.persistentEntity());
        query.append(tableName).append(SPACE);
        if (tableAlias != null) {
            query.append(getTableAsKeyword()).append(tableAlias);
        }
        buildWhereClause(annotationMetadata, definition.predicate(), queryState);
        Selection<?> returningSelection = definition.returningSelection();
        if (returningSelection != null) {
            if (!getDialect().supportsDeleteReturning()) {
                throw new IllegalStateException("Dialect: " + getDialect() + " doesn't support DELETE ... RETURNING clause");
            }
            queryString.append(RETURNING);
            buildSelect(
                annotationMetadata,
                queryState,
                returningSelection,
                false
            );
        }
        return QueryResult.of(
            queryState.getFinalQuery(),
            queryState.getQueryParts(),
            queryState.getParameterBindings()
        );
    }

    /**
     * Should aliases be used in batch statements.
     *
     * @param persistentEntity   the persistent entity
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
        return queryString.append("DELETE ").append(FROM_CLAUSE);
    }

    /**
     * Encode the given query into the encoded query instance.
     *
     * @param query              The query
     * @param entity             The root entity
     * @param annotationMetadata The annotation metadata
     * @param sort               The sort
     * @param nativeQuery        Whether the query is native query, in which case sort field names will be supplied by the user and not verified
     * @param tableAlias         The table alias
     * @return The encoded query
     */
    @NonNull
    public String buildOrderBy(String query,
                               @NonNull PersistentEntity entity,
                               @NonNull AnnotationMetadata annotationMetadata,
                               @NonNull Sort sort,
                               boolean nativeQuery,
                               @Nullable
                               String tableAlias) {
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
            buff.append(buildPropertyByName(property, query, entity, annotationMetadata, nativeQuery, tableAlias));
            if (ignoreCase) {
                buff.append(")");
            }
            buff.append(SPACE).append(order.getDirection());
            if (i.hasNext()) {
                buff.append(",");
            }
        }

        return buff.toString();
    }

    /**
     * Encode the given property retrieval into a query instance.
     * For example, property name might be encoded as {@code `person_.name`} using
     * its path and table's alias.
     *
     * @param propertyName       The name of the property
     * @param query              The query
     * @param entity             The root entity
     * @param annotationMetadata The annotation metadata
     * @param nativeQuery        Whether the query is native query, in which case the property name will be supplied by the user and not verified
     * @param tableAlias         The table alias
     * @return The encoded query
     */
    public String buildPropertyByName(@NonNull String propertyName,
                                      @NonNull String query,
                                      @NonNull PersistentEntity entity,
                                      @NonNull AnnotationMetadata annotationMetadata,
                                      boolean nativeQuery,
                                      @Nullable
                                      String tableAlias) {
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
        if (assocCount > 0 && computePropertyPaths() && associations.get(assocCount - 1).isEmbedded()) {
            associations.remove(assocCount - 1);
        }

        StringBuilder buff = new StringBuilder();
        String aliasName = tableAlias == null ? getAliasName(entity) : tableAlias;
        if (associations.isEmpty()) {
            buff.append(aliasName);
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
                    buff.append(aliasName).append(DOT);
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
            if (jsonEntityColumn != null) {
                appendJsonProjection(buff, path.getProperty().getDataType());
            }
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
    protected static String asPath(List<Association> associations, PersistentProperty property) {
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
     * Join associations as path.
     *
     * @param associations The associations
     * @return joined path
     */
    private static String asPath(List<Association> associations) {
        StringJoiner joiner = new StringJoiner(".");
        for (Association association : associations) {
            joiner.add(association.getName());
        }
        return joiner.toString();
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
     * @param entity             the persistent entity
     * @return true if {@link EntityRepresentation} annotation with JSON type is used for the repository method
     */
    protected boolean isJsonEntity(AnnotationMetadata annotationMetadata, PersistentEntity entity) {
        boolean jsonEntity = DataAnnotationUtils.hasJsonEntityRepresentationAnnotation(annotationMetadata);
        if (jsonEntity) {
            checkDialectSupportsJsonEntity(entity);
        }
        return jsonEntity;
    }

    protected final void appendExpression(AnnotationMetadata annotationMetadata,
                                          StringBuilder query,
                                          QueryState queryState,
                                          Expression<?> expression,
                                          boolean isProjection) {
        if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            appendPropertyRef(annotationMetadata, query, queryState, persistentPropertyPath.getPropertyPath(), isProjection);
        } else if (expression instanceof ParameterExpression<?> parameterExpression) {
            if (expression instanceof BindingParameter bindingParameter) {
                queryState.pushParameter(bindingParameter, newBindingContext(null));
            } else {
                throw new IllegalArgumentException("Unknown parameter: " + parameterExpression);
            }
        } else if (expression instanceof LiteralExpression<?> literalExpression) {
            query.append(asLiteral(literalExpression.getValue()));
        } else {
            throw new IllegalArgumentException("Unsupported expression type: " + expression.getClass());
        }
    }

    protected final void appendPropertyRef(AnnotationMetadata annotationMetadata,
                                           StringBuilder query,
                                           QueryState queryState,
                                           PersistentPropertyPath pp,
                                           boolean isProjection) {
        if (computePropertyPaths() && pp.getProperty() instanceof Embedded) {
            throw new IllegalArgumentException("Embedded are not allowed as an expression!");
        }
        QueryPropertyPath propertyPath = queryState.findProperty(pp);
        String tableAlias = propertyPath.getTableAlias();
        String readTransformer = isProjection ? getDataTransformerReadValue(tableAlias, propertyPath.getProperty()).orElse(null) : null;
        if (readTransformer != null) {
            query.append(readTransformer);
            return;
        }
        if (tableAlias != null) {
            query.append(tableAlias).append(DOT);
        }
        boolean computePropertyPaths = computePropertyPaths();
        boolean jsonEntity = isJsonEntity(annotationMetadata, queryState.entity);
        if (computePropertyPaths && !jsonEntity) {
            query.append(propertyPath.getColumnName());
        } else {
            if (jsonEntity) {
                String jsonEntityColumn = getJsonEntityColumn(annotationMetadata);
                if (jsonEntityColumn != null) {
                    query.append(jsonEntityColumn).append(DOT);
                    PersistentProperty property = propertyPath.getProperty();
                    if (property == queryState.entity.getIdentity()) {
                        query.append('"').append(property.getPersistedName()).append('"');
                    } else {
                        query.append(propertyPath.getPath());
                    }
                    DataType dataType = propertyPath.getProperty().getDataType();
                    appendJsonProjection(query, dataType);
                }
            } else {
                query.append(propertyPath.getPath());
            }
        }
    }

    /**
     * Appends value projection for JSON View field.
     *
     * @param sb       the string builder
     * @param dataType the property data type
     */
    private void appendJsonProjection(StringBuilder sb, DataType dataType) {
        if (dataType.isNumeric() || dataType == DataType.BOOLEAN) {
            // Boolean is represented as number in Oracle (which only supports json view)
            sb.append(".numberOnly()");
        } else if (dataType == DataType.STRING) {
            sb.append(".stringOnly()");
        } else if (dataType == DataType.TIMESTAMP) {
            sb.append(".timestamp()");
        } else if (dataType == DataType.DATE) {
            sb.append(".date()");
        }
    }

    private void appendConcat(StringBuilder writer, Collection<Runnable> partsWriters) {
        if (getDialect() == Dialect.ORACLE) {
            for (Iterator<Runnable> iterator = partsWriters.iterator(); iterator.hasNext(); ) {
                iterator.next().run();
                if (iterator.hasNext()) {
                    writer.append(" || ");
                }
            }
        } else {
            writer.append("CONCAT(");
            for (Iterator<Runnable> iterator = partsWriters.iterator(); iterator.hasNext(); ) {
                iterator.next().run();
                if (iterator.hasNext()) {
                    writer.append(COMMA);
                }
            }
            writer.append(")");
        }
    }

    @Override
    public String buildLimitAndOffset(long limit, long offset) {
        StringBuilder builder = new StringBuilder();
        appendLimitAndOffset(getDialect(), limit, offset, builder);
        return builder.toString();
    }

    /**
     * Append limit and offset.
     *
     * @param dialect The dialect
     * @param limit   The limit
     * @param offset  The offset
     * @param builder The builder
     */
    protected void appendLimitAndOffset(Dialect dialect, long limit, long offset, StringBuilder builder) {
        boolean hasLimit = limit > 0;
        boolean hasOffset = offset > 0;
        if (!hasLimit && !hasOffset) {
            return;
        }
        builder.append(' ');
        switch (dialect) {
            case SQL_SERVER -> {
                // SQL server requires OFFSET always
                if (hasOffset) {
                    builder.append("OFFSET ").append(offset).append(" ROWS ");
                } else {
                    builder.append("OFFSET 0 ROWS ");
                }
                if (hasLimit) {
                    builder.append("FETCH NEXT ").append(limit).append(" ROWS ONLY");
                }
            }
            case ORACLE -> {
                if (hasOffset) {
                    builder.append("OFFSET ").append(offset).append(" ROWS");
                }
                if (hasLimit) {
                    if (hasOffset) {
                        builder.append(" ");
                    }
                    builder.append("FETCH NEXT ").append(limit).append(" ROWS ONLY");
                }
            }
            default -> {
                if (hasLimit) {
                    builder.append("LIMIT ").append(limit);
                }
                if (hasOffset) {
                    if (hasLimit) {
                        builder.append(" ");
                    }
                    builder.append("OFFSET ").append(offset);
                }
            }
        }
    }

    protected record QueryBuilder(AtomicInteger position,
                                  List<QueryParameterBinding> parameterBindings,
                                  StringBuilder query,
                                  List<String> queryParts) {

        public QueryBuilder() {
            this(new AtomicInteger(0), new ArrayList<>(), new StringBuilder(), new ArrayList<>());
        }
    }

    /**
     * The state of the query.
     */
    @Internal
    protected final class QueryState implements PropertyParameterCreator {
        private final AbstractSqlLikeQueryBuilder2.QueryBuilder queryBuilder;
        private final String rootAlias;
        private final Map<String, JoinPath> appliedJoinPaths = new LinkedHashMap<>();
        private final boolean allowJoins;
        private final BaseQueryDefinition baseQueryDefinition;
        private final boolean escape;
        private final PersistentEntity entity;
        private List<JoinPath> joinPaths = new ArrayList<>();

        private QueryState(AbstractSqlLikeQueryBuilder2.QueryBuilder queryBuilder, BaseQueryDefinition query, boolean allowJoins, boolean useAlias) {
            this(queryBuilder, query, allowJoins, useAlias, null);
        }

        private QueryState(AbstractSqlLikeQueryBuilder2.QueryBuilder queryBuilder, BaseQueryDefinition query, boolean allowJoins, boolean useAlias, String tableAliasPrefix) {
            this.queryBuilder = queryBuilder;
            this.allowJoins = allowJoins;
            this.baseQueryDefinition = query;
            this.entity = query.persistentEntity();
            this.escape = AbstractSqlLikeQueryBuilder2.this.shouldEscape(entity);
            this.rootAlias = useAlias || tableAliasPrefix != null ? (tableAliasPrefix == null ? "" : tableAliasPrefix) + AbstractSqlLikeQueryBuilder2.this.getAliasName(entity) : null;
        }

        public QueryState(BaseQueryDefinition query, boolean allowJoins, boolean useAlias) {
            this(new QueryBuilder(), query, allowJoins, useAlias);
        }

        /**
         * @return The root alias
         */
        @Nullable
        public String getRootAlias() {
            return rootAlias;
        }

        /**
         * @return The entity
         */
        public PersistentEntity getEntity() {
            return entity;
        }

        public String getFinalQuery() {
            if (!queryBuilder.query.isEmpty() || !queryBuilder.queryParts.isEmpty()) {
                queryBuilder.queryParts.add(queryBuilder.query.toString());
                queryBuilder.query.setLength(0);
            }
            StringBuilder sb = new StringBuilder(queryBuilder.queryParts.get(0));
            int i = 1;
            for (int k = 1; k < queryBuilder.queryParts.size(); k++) {
                QueryParameterBinding queryParameterBinding = queryBuilder.parameterBindings.get(i - 1);
                if (queryParameterBinding.getRole() == null) {
                    Placeholder placeholder = formatParameter(i++);
                    sb.append(placeholder.name);
                    sb.append(queryBuilder.queryParts.get(k));
                } // Avoid create a placeholder for a role parameter
            }
            return sb.toString();
        }

        public List<String> getQueryParts() {
            return queryBuilder.queryParts;
        }

        /**
         * @return The query string
         */
        public StringBuilder getQuery() {
            return queryBuilder.query;
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
        public BaseQueryDefinition baseQueryDefinition() {
            return baseQueryDefinition;
        }

        /**
         * Constructs a new parameter placeholder.
         *
         * @return The parameter
         */
        private Placeholder newParameter() {
            return formatParameter(queryBuilder.position.incrementAndGet());
        }

        @Nullable
        public String findJoinAlias(String path) {
            JoinPath joinPath = appliedJoinPaths.get(path);
            return joinPath == null ? null : joinPath.getAlias().orElseThrow();
        }

        @NonNull
        public String getJoinAlias(String path) {
            String joinAlias = findJoinAlias(path);
            if (joinAlias == null) {
                throw new IllegalArgumentException("Property is not joined at path: " + path);
            }
            return joinAlias;
        }

        /**
         * Applies a join for the given association.
         *
         * @param joinPath The join path
         */
        public void applyJoin(@NonNull JoinPath joinPath) {
            joinPaths.add(joinPath);
            if (appliedJoinPaths.containsKey(joinPath.getPath())) {
                return;
            }
            Optional<JoinPath> ojp = baseQueryDefinition().getJoinPath(joinPath.getPath());
            if (ojp.isPresent()) {
                joinPath = ojp.get();
            }
            Join.Type jt = joinPath.getJoinType();
            String jpAlias = joinPath.getAlias().orElse(null);

            Association[] associationPath = joinPath.getAssociationPath();
            if (ArrayUtils.isEmpty(associationPath)) {
                throw new IllegalArgumentException("Invalid association path [" + joinPath.getPath() + "]");
            }
            StringJoiner pathSoFar = new StringJoiner(".");
            for (int i = 0; i < associationPath.length; i++) {
                Association association = associationPath[i];
                pathSoFar.add(association.getName());

                if (association.isEmbedded()) {
                    continue;
                }

                String currentPath = pathSoFar.toString();
                JoinPath existingJoinPath = appliedJoinPaths.get(currentPath);
                if (existingJoinPath == null) {
                    JoinPath joinPathToUse = baseQueryDefinition.getJoinPath(currentPath).orElse(null);
                    if (joinPathToUse == null) {
                        joinPathToUse = new JoinPath(
                            currentPath,
                            Arrays.copyOfRange(associationPath, 0, i + 1),
                            jt,
                            jpAlias);
                    }

                    String currentAlias = getAliasName(joinPathToUse);

                    joinPathToUse = joinPathToUse.withAlias(currentAlias);

                    appliedJoinPaths.put(currentPath, joinPathToUse);
                }
            }
        }

        private String getAliasName(JoinPath joinPath) {
            return joinPath.getAlias().orElseGet(() -> {
                String joinPathAlias = getPathOnlyAliasName(joinPath);

                // if "root association" has a declared alias, don't add entity alias as a prefix to match behavior of @Join(alias= "...")
                if (joinPath.getAssociationPath()[0].hasDeclaredAliasName()) {
                    return joinPathAlias;
                }

                PersistentEntity owner = joinPath.getAssociationPath()[0].getOwner();
                String ownerAlias;
                if (owner.equals(entity)) {
                    if (rootAlias == null) {
                        ownerAlias = AbstractSqlLikeQueryBuilder2.this.getAliasName(owner);
                    } else {
                    ownerAlias = rootAlias;
                    }
                } else {
                    ownerAlias = AbstractSqlLikeQueryBuilder2.this.getAliasName(owner);
                }
                if (ownerAlias.endsWith("_") && joinPathAlias.startsWith("_")) {
                    return ownerAlias + joinPathAlias.substring(1);
                } else {
                    return ownerAlias + joinPathAlias;
                }
            });
        }

        /**
         * Generates the JOIN query.
         */
        public void generateJoinQuery() {
            for (JoinPath joinPath : appliedJoinPaths.values()) {
                List<Association> joinedTablePath = new ArrayList<>(5);
                List<Association> joinAssociationsPath = new ArrayList<>(5);

                List<Association> previousAssociations = joinPath.getLeadingAssociations();

                for (int i = previousAssociations.size(); i-- > 0; ) {
                    Association association = previousAssociations.get(i);
                    if (association.isEmbedded()) {
                        joinAssociationsPath.add(0, association);
                        continue;
                    }
                    joinedTablePath = previousAssociations.subList(0, i + 1);
                    break;
                }

                String lastJoinAlias;
                if (joinedTablePath.isEmpty()) {
                    lastJoinAlias = rootAlias;
                } else {
                    String associatedJoinedTablePath = asPath(joinedTablePath);
                    JoinPath joinPath1 = appliedJoinPaths.get(associatedJoinedTablePath);
                    if (joinPath1 == null) {
                        throw new IllegalStateException("Path " + associatedJoinedTablePath + " not found. All: " + appliedJoinPaths.keySet());
                    }
                    lastJoinAlias = joinPath1.getAlias().orElseThrow();
                }
                generateJoin(
                    joinPath,
                    new PersistentAssociationPath(
                        joinAssociationsPath,
                        joinPath.getAssociation()
                    ),
                    lastJoinAlias
                );
            }
        }

        private void generateJoin(JoinPath joinPath, PersistentAssociationPath joinAssociation, String lastJoinAlias) {
            buildJoin(
                resolveJoinType(joinPath.getJoinType()),
                queryBuilder.query,
                this,
                joinAssociation,
                findOwner(entity, joinAssociation),
                joinPath.getAlias().orElseThrow(),
                lastJoinAlias
            );
        }

        private PersistentEntity findOwner(PersistentEntity mainEntity, PersistentAssociationPath joinAssociation) {
            PersistentEntity owner = joinAssociation.getAssociation().getOwner();
            if (!owner.isEmbeddable()) {
                return owner;
            }
            List<Association> associations = joinAssociation.getAssociations();
            ListIterator<Association> listIterator = associations.listIterator(associations.size());
            while (listIterator.hasPrevious()) {
                Association association = listIterator.previous();
                if (!association.getOwner().isEmbeddable()) {
                    return association.getOwner();
                }
            }
            return mainEntity;
        }

        /**
         * Checks if the path is joined already.
         *
         * @param associationPath The association path.
         * @return true if joined
         */
        public boolean isJoined(String associationPath) {
            return appliedJoinPaths.containsKey(associationPath);
        }

        /**
         * @return Should escape the query
         */
        public boolean shouldEscape() {
            return escape;
        }

        /**
         * The parameter binding.
         *
         * @return The parameter binding
         */
        public List<QueryParameterBinding> getParameterBindings() {
            return queryBuilder.parameterBindings;
        }

        @Override
        public void pushParameter(@NonNull BindingParameter bindingParameter, @NonNull BindingParameter.BindingContext bindingContext) {
            Placeholder placeholder = newParameter();
            bindingContext = bindingContext
                .index(queryBuilder.position.get() + 1);
            if (bindingContext.getName() == null) {
                bindingContext = bindingContext.name(placeholder.key());
            }
            queryBuilder.parameterBindings.add(
                bindingParameter.bind(bindingContext)
            );
            queryBuilder.queryParts.add(queryBuilder.query.toString());
            queryBuilder.query.setLength(0);
        }

        /**
         * Adds query parameter binding.
         *
         * @param parameterBinding the query parameter binding
         */
        public void pushParameter(@NonNull QueryParameterBinding parameterBinding) {
            queryBuilder.parameterBindings.add(parameterBinding);
            queryBuilder.queryParts.add(queryBuilder.query.toString());
            queryBuilder.query.setLength(0);
        }

        public List<JoinPath> getJoinPaths() {
            return joinPaths;
        }

        public void setJoinPaths(List<JoinPath> joinPaths) {
            this.joinPaths = joinPaths;
        }

        @NonNull
        private QueryPropertyPath findProperty(String propertyPath) {
            PersistentPropertyPath pp = entity.getPropertyPath(propertyPath);
            if (pp != null) {
                return findPropertyInternal(pp);
            } else if (TypeRole.ID.equals(propertyPath) && entity.getIdentity() != null) {
                // special case handling for ID
                return new QueryPropertyPath(
                    new PersistentPropertyPath(Collections.emptyList(), entity.getIdentity(), entity.getIdentity().getName()),
                    rootAlias
                );
            }
            throw new IllegalArgumentException("Cannot select or update non-existent property path: " + propertyPath);
        }

        @NonNull
        private QueryPropertyPath findProperty(PersistentPropertyPath propertyPath) {
            return findPropertyInternal(propertyPath);
        }

        @NonNull
        private QueryPropertyPath findPropertyInternal(PersistentPropertyPath propertyPath) {
            if (propertyPath.getAssociations().isEmpty()) {
                return new QueryPropertyPath(propertyPath, rootAlias);
            }
            PersistentProperty property = propertyPath.getProperty();
            List<Association> joinPath = new ArrayList<>(propertyPath.getAssociations());
            ListIterator<Association> listIterator = joinPath.listIterator(joinPath.size());

            while (listIterator.hasPrevious()) {
                Association association = listIterator.previous();
                if (association.isEmbedded()) {
                    listIterator.remove();
                } else if (PersistentEntityUtils.isAccessibleWithoutJoin(association, property)) {
                    property = association;
                    // We don't need to join to access the id of the relation if it is not a foreign key association
                    listIterator.remove();
                } else {
                    break;
                }
            }
            if (!joinPath.isEmpty()) {
                // 'joinPath.prop' should be represented as a path of 'prop' with a join alias
                return new QueryPropertyPath(
                    new PersistentPropertyPath(Collections.emptyList(), property),
                    getRequiredJoinPathAlias(asPath(joinPath))
                );
            }
            return new QueryPropertyPath(propertyPath, rootAlias);
        }

        @NonNull
        private String getRequiredJoinPathAlias(String path) {
            if (!isAllowJoins()) {
                throw new IllegalArgumentException("Joins cannot be used in a DELETE or UPDATE operation and path: " + path);
            }
            return getJoinAlias(path);
        }

    }

    /**
     * Represents a placeholder in query.
     *
     * @param name The name of the placeholder
     * @param key  The key to set the value of the placeholder
     */
    public record Placeholder(String name, String key) {

        @Override
        public String toString() {
            return name;
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
            return AbstractSqlLikeQueryBuilder2.this.getNamingStrategy(propertyPath);
        }

        /**
         * @return should escape
         */
        public boolean shouldEscape() {
            return AbstractSqlLikeQueryBuilder2.this.shouldEscape(propertyPath.findPropertyOwner().orElse(propertyPath.getProperty().getOwner()));
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

    /**
     * The predicate visitor to construct the query.
     */
    protected class SqlPredicateVisitor implements AdvancedPredicateVisitor<PersistentPropertyPath> {

        protected final PersistentEntity persistentEntity;
        protected final String tableAlias;
        protected final StringBuilder query;
        protected final QueryState queryState;
        protected final AnnotationMetadata annotationMetadata;

        protected SqlPredicateVisitor(QueryState queryState, AnnotationMetadata annotationMetadata) {
            this.queryState = queryState;
            this.annotationMetadata = annotationMetadata;
            persistentEntity = queryState.getEntity();
            tableAlias = queryState.getRootAlias();
            query = queryState.getQuery();
        }

        @Override
        public PersistentPropertyPath getRequiredProperty(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            return persistentPropertyPath.getPropertyPath();
        }

        private void visitPredicate(IExpression<Boolean> expression) {
            if (expression instanceof RenderablePredicate renderablePredicate) {
                renderablePredicate.render(query, queryState);
            } else if (expression instanceof IPredicate predicateVisitable) {
                predicateVisitable.visitPredicate(this);
            } else if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?>) {
                visitIsTrue(expression);
            } else {
                throw new IllegalStateException("Unknown boolean expression: " + expression);
            }
        }

        @Override
        public void visit(ConjunctionPredicate conjunction) {
            if (conjunction.getPredicates().isEmpty()) {
                return;
            }
            boolean requiresBracket = query.charAt(query.length() - 1) != '(';
            if (requiresBracket) {
                query.append(OPEN_BRACKET);
            }
            visitConjunctionPredicates(conjunction.getPredicates());
            if (requiresBracket) {
                query.append(CLOSE_BRACKET);
            }
        }

        private void visitConjunctionPredicates(Collection<? extends IExpression<Boolean>> predicates) {
            Iterator<? extends IExpression<Boolean>> iterator = predicates.iterator();
            boolean appendLogicalAnd = true;
            while (iterator.hasNext()) {
                IExpression<Boolean> expression = iterator.next();
                if (expression instanceof ConjunctionPredicate conjunctionPredicate) {
                    Collection<? extends IExpression<Boolean>> conjunctionPredicates = conjunctionPredicate.getPredicates();
                    if (CollectionUtils.isEmpty(conjunctionPredicates)) {
                        // Nothing was added to the query so skip adding AND
                        appendLogicalAnd = false;
                    } else {
                        visitConjunctionPredicates(conjunctionPredicates);
                    }
                } else {
                    visitPredicate(expression);
                }
                if (appendLogicalAnd && iterator.hasNext()) {
                    query.append(LOGICAL_AND);
                }
            }
        }

        @Override
        public void visit(DisjunctionPredicate disjunction) {
            if (disjunction.getPredicates().isEmpty()) {
                return;
            }
            query.append(OPEN_BRACKET);
            visitDisjunctionPredicates(disjunction.getPredicates());
            query.append(CLOSE_BRACKET);
        }

        private void visitDisjunctionPredicates(Collection<? extends IExpression<Boolean>> predicates) {
            Iterator<? extends IExpression<Boolean>> iterator = predicates.iterator();
            while (iterator.hasNext()) {
                IExpression<Boolean> expression = iterator.next();
                if (expression instanceof DisjunctionPredicate disjunctionPredicate) {
                    visitDisjunctionPredicates(disjunctionPredicate.getPredicates());
                } else {
                    visitPredicate(expression);
                }
                if (iterator.hasNext()) {
                    query.append(LOGICAL_OR);
                }
            }
        }

        @Override
        public void visit(NegatedPredicate negate) {
            IExpression<Boolean> negated = negate.getNegated();
            if (negated instanceof InPredicate<?> p) {
                visitIn(p.getExpression(), p.getValues(), true);
            } else {
                query.append(NOT).append(OPEN_BRACKET);
                if (negated instanceof ConjunctionPredicate conjunctionPredicate) {
                    visitConjunctionPredicates(conjunctionPredicate.getPredicates());
                } else if (negated instanceof DisjunctionPredicate disjunctionPredicate) {
                    visitDisjunctionPredicates(disjunctionPredicate.getPredicates());
                } else {
                    visitPredicate(negated);
                }
                query.append(CLOSE_BRACKET);
            }
        }

        @Override
        public void visit(LikePredicate likePredicate) {
            boolean supportsILike = getDialect() == Dialect.POSTGRES;
            boolean isCaseInsensitive = !supportsILike && likePredicate.isCaseInsensitive();
            if (isCaseInsensitive) {
                query.append("LOWER(");
            }
            appendExpression(likePredicate.getExpression());
            if (isCaseInsensitive) {
                query.append(")");
            }
            if (likePredicate.isNegated()) {
                query.append(" NOT");
            }
            if (likePredicate.isCaseInsensitive() && supportsILike) {
                query.append(" ILIKE ");
            } else {
                query.append(" LIKE ");
            }
            Expression<String> pattern = likePredicate.getPattern();
            if (isCaseInsensitive) {
                if (pattern instanceof LiteralExpression<String> literalExpression) {
                    query.append(literalExpression.getValue().toUpperCase());
                } else {
                    query.append("LOWER(");
                    appendExpression(pattern);
                    query.append(")");
                }
            } else {
                appendExpression(pattern);
            }

            Expression<Character> escapeChar = likePredicate.getEscapeChar();
            if (escapeChar != null) {
                query.append(" ESCAPE ");
                appendExpression(escapeChar);
            }
        }

        @Override
        public void visit(ExistsSubqueryPredicate existsSubqueryPredicate) {
            query.append("EXISTS");
            appendExpression(existsSubqueryPredicate.getSubquery());
        }

        @Override
        public void visitEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            if (leftExpression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
                PersistentPropertyPath propertyPath = persistentPropertyPath.getPropertyPath();
                PersistentProperty property = propertyPath.getProperty();
                if (computePropertyPaths() && property instanceof Association) {
                    List<IPredicate> predicates = new ArrayList<>();
                    PersistentEntityUtils.traverse(propertyPath, pp ->
                        predicates.add(new BinaryPredicate(
                            new DefaultPersistentPropertyPath<>(pp, null),
                            rightExpression,
                            ignoreCase ? PredicateBinaryOp.EQUALS_IGNORE_CASE : PredicateBinaryOp.EQUALS
                        ))
                    );
                    if (predicates.size() == 1) {
                        predicates.iterator().next().visitPredicate(this);
                    } else {
                        visit(new ConjunctionPredicate(predicates));
                    }
                    return;
                }
            }
            if (ignoreCase) {
                appendCaseInsensitiveOp(leftExpression, rightExpression, " = ");
            } else {
                appendBinaryOperation(" = ", leftExpression, rightExpression);
            }
        }

        @Override
        public void visitNotEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            if (leftExpression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
                PersistentPropertyPath propertyPath = persistentPropertyPath.getPropertyPath();
                PersistentProperty property = propertyPath.getProperty();
                if (computePropertyPaths() && property instanceof Association) {
                    List<IPredicate> predicates = new ArrayList<>();
                    PersistentEntityUtils.traverse(propertyPath, pp ->
                        predicates.add(new BinaryPredicate(
                            new DefaultPersistentPropertyPath<>(pp, null),
                            rightExpression,
                            ignoreCase ? PredicateBinaryOp.NOT_EQUALS_IGNORE_CASE : PredicateBinaryOp.NOT_EQUALS
                        ))
                    );
                    if (predicates.size() == 1) {
                        predicates.iterator().next().visitPredicate(this);
                    } else {
                        visit(new ConjunctionPredicate(predicates));
                    }
                    return;
                }
            }
            if (ignoreCase) {
                appendCaseInsensitiveOp(leftExpression, rightExpression, " != ");
            } else {
                appendBinaryOperation(" != ", leftExpression, rightExpression);
            }
        }

        @Override
        public void visitGreaterThan(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendBinaryOperation(" > ", leftExpression, rightExpression);
        }

        @Override
        public void visitGreaterThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendBinaryOperation(" >= ", leftExpression, rightExpression);
        }

        @Override
        public void visitLessThan(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendBinaryOperation(" < ", leftExpression, rightExpression);
        }

        @Override
        public void visitLessThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendBinaryOperation(" <= ", leftExpression, rightExpression);
        }

        @Override
        public void visitStartsWith(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            appendLikeConcatComparison(leftExpression, rightExpression, ignoreCase, "?", "'%'");
        }

        @Override
        public void visitContains(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase) {
            appendLikeConcatComparison(leftExpression, expression, ignoreCase, "'%'", "?", "'%'");
        }

        @Override
        public void visitEndsWith(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase) {
            appendLikeConcatComparison(leftExpression, expression, ignoreCase, "'%'", "?");
        }

        private void appendLikeConcatComparison(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase, String... parts) {
            boolean isPostgres = getDialect() == Dialect.POSTGRES;
            if (ignoreCase && !isPostgres) {
                query.append("LOWER(");
                appendExpression(leftExpression);
                query.append(")");
            } else {
                appendExpression(leftExpression);
            }
            if (isPostgres) {
                query.append(" ILIKE ");
            } else {
                query.append(" LIKE ");
            }
            appendConcat(query, Arrays.stream(parts).map(p -> {
                if ("?".equals(p)) {
                    if (ignoreCase && !isPostgres) {
                        return (Runnable) () -> {
                            query.append("LOWER(");
                            appendExpression(expression, leftExpression);
                            query.append(")");
                        };
                    } else {
                        return (Runnable) () -> appendExpression(expression, leftExpression);
                    }
                }
                return (Runnable) () -> query.append(p);
            }).toList());
        }

        @Override
        public void visitIdEquals(Expression<?> expression) {
            if (persistentEntity.hasCompositeIdentity()) {
                new ConjunctionPredicate(
                    Arrays.stream(persistentEntity.getCompositeIdentity())
                        .map(prop -> {
                                PersistentPropertyPath propertyPath = asPersistentPropertyPath(prop);
                                return new BinaryPredicate(
                                    new DefaultPersistentPropertyPath<>(propertyPath, null),
                                    expression,
                                    PredicateBinaryOp.EQUALS
                                );
                            }
                        )
                        .toList()
                ).visitPredicate(this);
            } else if (persistentEntity.hasIdentity()) {
                new BinaryPredicate(
                    new DefaultPersistentPropertyPath<>(new PersistentPropertyPath(persistentEntity.getIdentity()), null),
                    expression,
                    PredicateBinaryOp.EQUALS
                ).visitPredicate(this);
            } else {
                throw new IllegalStateException("No ID found for entity: " + persistentEntity.getName());
            }
        }

        protected final void appendPropertyRef(PersistentPropertyPath propertyPath) {
            AbstractSqlLikeQueryBuilder2.this.appendPropertyRef(annotationMetadata, query, queryState, propertyPath, false);
        }

        private void appendBinaryOperation(@NonNull String operator, @NonNull Expression<?> leftExpression, @NonNull Expression<?> rightExpression) {
            appendExpression(leftExpression, null);
            query.append(operator);
            appendExpression(rightExpression, leftExpression);
        }

        private void appendExpression(Expression<?> expression) {
            appendExpression(expression, null);
        }

        protected final void appendExpression(Expression<?> expression, @Nullable Expression<?> boundedExpression) {
            CriteriaUtils.requireIExpression(expression).visitExpression(new ExpressionVisitor() {

                @Override
                public void visit(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
                    appendPropertyRef(persistentPropertyPath.getPropertyPath());
                }

                @Override
                public void visit(PersistentEntityRoot<?> entityRoot) {
                    visit(new IdExpression<>(entityRoot));
                }

                @Override
                public void visit(LiteralExpression<?> literalExpression) {
                    query.append(asLiteral(literalExpression));
                }

                @Override
                public void visit(UnaryExpression<?> unaryExpression) {
                    Expression<?> expression = unaryExpression.getExpression();
                    switch (unaryExpression.getType()) {
                        case SUM, AVG, MAX, MIN, UPPER, LOWER ->
                            appendFunction(unaryExpression.getType().name(), expression);
                        default ->
                            throw new IllegalStateException(UNSUPPORTED_EXPRESSION + unaryExpression.getType());
                    }
                }

                @Override
                public void visit(BinaryExpression<?> binaryExpression) {
                    Expression<?> left = binaryExpression.getLeft();
                    Expression<?> right = binaryExpression.getRight();
                    switch (binaryExpression.getType()) {
                        case SUM -> {
                            appendExpression(left);
                            query.append(" + ");
                            appendExpression(right);
                        }
                        case CONCAT -> appendFunction("CONCAT", List.of(left, right));
                        default ->
                            throw new IllegalStateException(UNSUPPORTED_EXPRESSION + binaryExpression.getType());
                    }
                }

                @Override
                public void visit(IdExpression<?, ?> idExpression) {
                    PersistentEntity persistentEntity = idExpression.getRoot().getPersistentEntity();
                    if (persistentEntity.hasCompositeIdentity()) {
                        throw new IllegalStateException("ID expression with composite IDs not allowed");
                    }
                    if (persistentEntity.getIdentityProperties().size() > 1) {
                        throw new IllegalStateException("ID expression with multiple IDs not allowed");
                    }
                    PersistentProperty identity = persistentEntity.getIdentity();
                    appendPropertyRef(new PersistentPropertyPath(identity));
                }

                @Override
                public void visit(FunctionExpression<?> functionExpression) {
                    appendFunction(functionExpression.getName(), functionExpression.getExpressions());
                }

                @Override
                public void visit(IParameterExpression<?> parameterExpression) {
                    appendBindingParameter(parameterExpression, findParameterBoundProperty(boundedExpression));
                }

                @Override
                public void visit(SubqueryExpression<?> subqueryExpression) {
                    query.append(subqueryExpression.getType().name());
                    visit(subqueryExpression.getSubquery());
                }

                @Override
                public void visit(PersistentEntitySubquery<?> subquery) {
                    AbstractPersistentEntityQuery<?, ?> abstractPersistentEntityQuery = (AbstractPersistentEntityQuery<?, ?>) subquery;
                    SelectQueryDefinition selectQueryDefinition = abstractPersistentEntityQuery.toSelectQueryDefinition();
                    String outerAlias = queryState.getRootAlias();
                    if (outerAlias == null) {
                        outerAlias = getAliasName(queryState.getEntity());
                    }
                    boolean requiresBrackets = query.charAt(query.length() - 1) != '(';
                    if (requiresBrackets) {
                        query.append("(");
                    }
                    buildQuery(AnnotationMetadata.EMPTY_METADATA, selectQueryDefinition, queryState.queryBuilder, false, outerAlias);
                    if (requiresBrackets) {
                        query.append(")");
                    }
                }
            });
        }

        private PersistentPropertyPath findParameterBoundProperty(Expression<?> binaryOpExpression) {
            // We want to find the property bound to the parameter
            if (binaryOpExpression == null) {
                return null;
            }
            if (binaryOpExpression instanceof UnaryExpression<?> unaryExpression) {
                return findParameterBoundProperty(unaryExpression.getExpression());
            }
            if (binaryOpExpression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
                return persistentPropertyPath.getPropertyPath();
            }
            return null;
        }

        private void appendFunction(String functionName, Expression<?> expression) {
            appendFunction(functionName, List.of(expression));
        }

        private void appendFunction(String functionName, List<Expression<?>> expressions) {
            query.append(functionName)
                .append(OPEN_BRACKET);
            for (Iterator<Expression<?>> iterator = expressions.iterator(); iterator.hasNext(); ) {
                Expression<?> expression = iterator.next();
                appendExpression(expression);
                if (iterator.hasNext()) {
                    query.append(COMMA);
                }
            }
            query.append(CLOSE_BRACKET);
        }

        private void appendBindingParameter(BindingParameter bindingParameter,
                                            @Nullable PersistentPropertyPath entityPropertyPath) {
            Runnable pushParameter = () -> {
                queryState.pushParameter(
                    bindingParameter,
                    newBindingContext(null, entityPropertyPath)
                );
            };
            if (entityPropertyPath == null) {
                pushParameter.run();
            } else {
                QueryPropertyPath qpp = queryState.findProperty(entityPropertyPath);
                String writeTransformer = getDataTransformerWriteValue(qpp.tableAlias, entityPropertyPath.getProperty()).orElse(null);
                if (writeTransformer != null) {
                    appendTransformed(query, writeTransformer, pushParameter);
                } else {
                    pushParameter.run();
                }
            }
        }

        private void appendCaseInsensitiveOp(Expression<?> leftExpression, Expression<?> expression, String operator) {
            query.append("LOWER(");
            appendExpression(leftExpression);
            query.append(")")
                .append(operator)
                .append("LOWER(");
            appendExpression(expression, leftExpression);
            query.append(")");
        }

        @Override
        public void visitIsFalse(Expression<?> expression) {
            appendUnaryCondition(" = FALSE", expression);
        }

        @Override
        public void visitIsNotNull(Expression<?> expression) {
            appendUnaryCondition(" IS NOT NULL", expression);
        }

        @Override
        public void visitIsNull(Expression<?> expression) {
            appendUnaryCondition(" IS NULL", expression);
        }

        @Override
        public void visitIsTrue(Expression<?> expression) {
            appendUnaryCondition(" = TRUE", expression);
        }

        @Override
        public void visitIsEmpty(Expression<?> expression) {
            appendEmptyExpression(" IS NULL" + " " + OR + StringUtils.SPACE, " = ''", " IS EMPTY", expression);
        }

        @Override
        public void visitIsNotEmpty(Expression<?> expression) {
            if (getDialect() == Dialect.ORACLE) {
                PersistentPropertyPath propertyPath = requireProperty(expression).getPropertyPath();
                // Oracle treats blank and null the same
                if (propertyPath.getProperty().isAssignable(CharSequence.class)) {
                    appendPropertyRef(propertyPath);
                    query.append(" IS NOT NULL");
                } else {
                    appendPropertyRef(propertyPath);
                    query.append(" IS NOT EMPTY");
                }
            } else {
                appendEmptyExpression(" IS NOT NULL" + " " + AND + StringUtils.SPACE, " <> ''", " IS NOT EMPTY", expression);
            }
        }

        private void appendEmptyExpression(String charSequencePrefix,
                                           String charSequenceSuffix,
                                           String listSuffix,
                                           Expression<?> expression) {
            if (((IExpression<?>) expression).getExpressionType().isTextual()) {
                appendExpression(expression);
                query.append(charSequencePrefix);
                appendExpression(expression);
                query.append(charSequenceSuffix);
            } else {
                appendExpression(expression);
                query.append(listSuffix);
            }
        }

        private void appendUnaryCondition(String sqlOp, Expression<?> expression) {
            appendExpression(expression);
            query.append(sqlOp);
        }

        @Override
        public void visitInBetween(Expression<?> value, Expression<?> from, Expression<?> to) {
            query.append(OPEN_BRACKET);
            appendExpression(value);
            query.append(" >= ");
            appendExpression(from, value);
            query.append(LOGICAL_AND);
            appendExpression(value);
            query.append(" <= ");
            appendExpression(to, value);
            query.append(CLOSE_BRACKET);
        }

        @Override
        public void visitIn(Expression<?> expression, Collection<?> values, boolean negated) {
            if (values.isEmpty()) {
                return;
            }
            PersistentPropertyPath propertyPath = requireProperty(expression).getPropertyPath();
            appendExpression(expression);
            query.append(negated ? " NOT IN (" : " IN (");
            boolean hasOneParameter = values.stream().filter(v -> v instanceof ParameterExpression).count() == 1;
            Iterator<?> iterator = values.iterator();
            while (iterator.hasNext()) {
                Object value = iterator.next();
                if (value instanceof ParameterExpression) {
                    BindingParameter.BindingContext bindingContext = newBindingContext(propertyPath);
                    if (hasOneParameter) {
                        bindingContext = bindingContext.expandable();
                    }
                    queryState.pushParameter((BindingParameter) value, bindingContext);
                } else {
                    appendExpression((Expression<?>) value);
                }
                if (iterator.hasNext()) {
                    query.append(COMMA);
                }
            }
            query.append(CLOSE_BRACKET);
        }

    }

    /**
     * The selection visitor to construct the query.
     */
    protected class SqlSelectionVisitor implements SelectionVisitor {

        protected final QueryState queryState;
        protected final StringBuilder query;
        protected final AnnotationMetadata annotationMetadata;
        protected final boolean distinct;
        protected final String tableAlias;
        protected final PersistentEntity entity;
        protected String columnAlias;
        private boolean isCompound;

        public SqlSelectionVisitor(QueryState queryState, AnnotationMetadata annotationMetadata, boolean distinct) {
            this.queryState = queryState;
            this.query = queryState.getQuery();
            this.annotationMetadata = annotationMetadata;
            this.distinct = distinct;
            this.tableAlias = queryState.getRootAlias();
            this.entity = queryState.getEntity();
        }

        @Override
        public void visit(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            PersistentPropertyPath propertyPath = persistentPropertyPath.getPropertyPath();
            PersistentProperty property = propertyPath.getProperty();
            if (isCompound) {
                // Compound property which is part of a DTO
                if (property instanceof Association association && !property.isEmbedded()) {
                    if (queryState.isJoined(propertyPath.getPath())) {
                        appendCompoundAssociationProjection(new PersistentAssociationPath(propertyPath.getAssociations(), association));
                    } else {
                        query.setLength(query.length() - 1);
                    }
                } else if (!propertyPath.getAssociations().isEmpty() && queryState.isJoined(propertyPath.getAssociationsPath())) {
                    appendPropertyProjection(findProperty(propertyPath.getPath()));
                } else {
                    appendCompoundPropertyProjection(propertyPath);
                }
            } else {
                if (distinct) {
                    query.append(DISTINCT);
                }
                if (property instanceof Association association && !property.isEmbedded()) {
                    appendAssociationProjection(new PersistentAssociationPath(propertyPath.getAssociations(), association));
                } else {
                    appendPropertyProjection(findProperty(propertyPath.getPath()));
                }
            }
        }

        @Override
        public void visit(AliasedSelection<?> aliasedSelection) {
            columnAlias = aliasedSelection.getAlias();
            aliasedSelection.getSelection().visitSelection(this);
            columnAlias = null;
        }

        @Override
        public void visit(PersistentEntityRoot<?> entityRoot) {
            if (distinct) {
                query.append(DISTINCT);
            }
            selectAllColumnsAndJoined();
        }

        @Override
        public void visit(PersistentEntitySubquery<?> subquery) {
            throw new IllegalStateException("Subquery not supported in selection");
        }

        @Override
        public void visit(CompoundSelection<?> compoundSelection) {
            if (distinct) {
                query.append(DISTINCT);
            }
            isCompound = true;
            Iterator<Selection<?>> iterator = compoundSelection.getCompoundSelectionItems().iterator();
            while (iterator.hasNext()) {
                Selection<?> selection = iterator.next();
                if (selection instanceof ISelection<?> selectionVisitable) {
                    selectionVisitable.visitSelection(this);
                } else {
                    throw new IllegalStateException("Unknown selection object: " + selection);
                }
                if (iterator.hasNext()) {
                    query.append(COMMA);
                }
            }
            isCompound = false;
        }

        @Override
        public void visit(LiteralExpression<?> literalExpression) {
            // Support alias?
            query.append(asLiteral(literalExpression.getValue()));
        }

        @Override
        public void visit(UnaryExpression<?> unaryExpression) {
            Expression<?> expression = unaryExpression.getExpression();
            switch (unaryExpression.getType()) {
                case SUM, AVG, MAX, MIN, UPPER, LOWER ->
                    appendFunction(unaryExpression.getType().name(), expression);
                case COUNT -> {
                    if (expression instanceof PersistentEntityRoot) {
                        appendRowCount(tableAlias);
                    } else if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
                        appendFunction("COUNT", persistentPropertyPath);
                    } else {
                        throw new IllegalStateException("Illegal expression: " + expression + " for count selection!");
                    }
                }
                case COUNT_DISTINCT -> {
                    if (expression instanceof PersistentEntityRoot) {
                        appendRowCountDistinct(tableAlias);
                    } else if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
                        appendFunction("COUNT(DISTINCT", persistentPropertyPath);
                        query.append(CLOSE_BRACKET);
                    } else {
                        throw new IllegalStateException("Illegal expression: " + expression + " for count distinct selection!");
                    }
                }
                default ->
                    throw new IllegalStateException(UNSUPPORTED_EXPRESSION + unaryExpression.getType());
            }
        }

        @Override
        public void visit(BinaryExpression<?> binaryExpression) {
            Expression<?> left = binaryExpression.getLeft();
            Expression<?> right = binaryExpression.getRight();
            switch (binaryExpression.getType()) {
                case SUM -> {
                    appendExpression(left);
                    query.append(" + ");
                    appendExpression(right);
                }
                case CONCAT -> appendFunction("CONCAT", List.of(left, right));
                default ->
                    throw new IllegalStateException(UNSUPPORTED_EXPRESSION + binaryExpression.getType());
            }
        }

        @Override
        public void visit(IdExpression<?, ?> idExpression) {
            // Support distinct?
            if (entity.hasCompositeIdentity()) {
                for (PersistentProperty identity : entity.getCompositeIdentity()) {
                    appendPropertyProjection(asQueryPropertyPath(queryState.getRootAlias(), identity));
                    query.append(COMMA);
                }
                query.setLength(query.length() - 1);
            } else if (entity.hasIdentity()) {
                List<PersistentProperty> identityProperties = entity.getIdentityProperties();
                if (identityProperties.isEmpty()) {
                    throw new IllegalArgumentException(CANNOT_QUERY_ON_ID_WITH_ENTITY_THAT_HAS_NO_ID);
                }
                for (PersistentProperty identity : identityProperties) {
                    appendPropertyProjection(asQueryPropertyPath(queryState.getRootAlias(), identity));
                }
            } else {
                throw new IllegalArgumentException(CANNOT_QUERY_ON_ID_WITH_ENTITY_THAT_HAS_NO_ID);
            }
        }

        @Override
        public void visit(FunctionExpression<?> functionExpression) {
            appendFunction(functionExpression.getName(), functionExpression.getExpressions());
        }

        /**
         * Appends the compound (part of entity or DTO) property projection.
         *
         * @param propertyPath The property path
         */
        @Internal
        protected void appendCompoundPropertyProjection(PersistentPropertyPath propertyPath) {
            PersistentEntity entity = propertyPath.getProperty().getOwner();
            boolean escape = shouldEscape(entity);
            NamingStrategy namingStrategy = getNamingStrategy(entity);
            int[] propertiesCount = new int[1];
            PersistentEntityUtils.traversePersistentProperties(propertyPath, traverseEmbedded(), (associations, p) -> {
                appendProperty(query, associations, p, namingStrategy, queryState.rootAlias, escape);
                propertiesCount[0]++;
            });
            query.setLength(query.length() - 1);
            if (StringUtils.isNotEmpty(columnAlias)) {
                if (propertiesCount[0] > 1) {
                    throw new IllegalStateException("Cannot apply a column alias: " + columnAlias + " with expanded property: " + propertyPath);
                }
                if (propertiesCount[0] == 1) {
                    query.append(AS_CLAUSE).append(columnAlias);
                }
            }
        }

        /**
         * Appends the compound (part of entity or DTO) association projection.
         *
         * @param propertyPath The property path
         */
        @Internal
        protected void appendCompoundAssociationProjection(PersistentAssociationPath propertyPath) {
            if (!query.isEmpty() && query.charAt(query.length() - 1) == ',') {
                // Strip last ,
                query.setLength(query.length() - 1);
            }
            selectAllColumnsFromJoinPaths(queryState.baseQueryDefinition().getJoinPaths(), null);
        }

        /**
         * Appends the compound (part of entity or DTO) association projection.
         *
         * @param propertyPath The property path
         */
        @Internal
        protected void appendCompoundProjection(PersistentPropertyPath propertyPath) {
            if (!query.isEmpty() && query.charAt(query.length() - 1) == ',') {
                // Strip last ,
                query.setLength(query.length() - 1);
            }
            selectAllColumnsFromJoinPaths(queryState.baseQueryDefinition().getJoinPaths(), null);
        }

        /**
         * Append the property projection.
         *
         * @param propertyPath The property
         */
        protected void appendPropertyProjection(QueryPropertyPath propertyPath) {
            boolean jsonEntity = isJsonEntity(annotationMetadata, entity);
            if (!computePropertyPaths() || jsonEntity) {
                query.append(propertyPath.getTableAlias()).append(DOT);
                String jsonEntityColumn = null;
                if (jsonEntity) {
                    jsonEntityColumn = getJsonEntityColumn(annotationMetadata);
                    if (jsonEntityColumn != null) {
                        checkDialectSupportsJsonEntity(entity);
                    }
                    query.append(jsonEntityColumn).append(DOT);
                }
                query.append(propertyPath.getPath());
                if (jsonEntityColumn != null) {
                    appendJsonProjection(query, propertyPath.getProperty().getDataType());
                }
                return;
            }
            String tableAlias = propertyPath.getTableAlias();
            boolean escape = propertyPath.shouldEscape();
            NamingStrategy namingStrategy = propertyPath.getNamingStrategy();
            boolean[] needsTrimming = {false};
            int[] propertiesCount = new int[1];

            PersistentEntityUtils.traversePersistentProperties(propertyPath.getAssociations(), propertyPath.getProperty(), traverseEmbedded(), (associations, property) -> {
                appendProperty(query, associations, property, namingStrategy, tableAlias, escape);
                needsTrimming[0] = true;
                propertiesCount[0]++;
            });
            if (needsTrimming[0]) {
                query.setLength(query.length() - 1);
            }
            if (StringUtils.isNotEmpty(columnAlias)) {
                if (propertiesCount[0] > 1) {
                    throw new IllegalStateException("Cannot apply a column alias: " + columnAlias + " with expanded property: " + propertyPath);
                }
                if (propertiesCount[0] == 1) {
                    query.append(AS_CLAUSE).append(columnAlias);
                }
            }
        }

        /**
         * Appends selection projection for the property which is association.
         *
         * @param associationPath the persistent property path
         */
        protected void appendAssociationProjection(PersistentAssociationPath associationPath) {
            String joinedPath = associationPath.getPath();
            if (!queryState.isJoined(joinedPath)) {
                query.setLength(query.length() - 1);
                return;
            }
            String joinAlias = queryState.findJoinAlias(associationPath.getPath());

            selectAllColumns(AnnotationMetadata.EMPTY_METADATA, associationPath.getAssociation().getAssociatedEntity(), joinAlias);

            Collection<JoinPath> joinPaths = queryState.baseQueryDefinition().getJoinPaths();
            List<JoinPath> newJoinPaths = new ArrayList<>(joinPaths.size());
            Map<JoinPath, String> joinAliasOverride = new HashMap<>();
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
                }
            }
            queryState.setJoinPaths(newJoinPaths);
            selectAllColumnsFromJoinPaths(newJoinPaths, joinAliasOverride);
        }

        /**
         * Appends a row count projection to the query string.
         *
         * @param logicalName The alias to the table name
         */
        protected void appendRowCount(String logicalName) {
            throw new IllegalStateException("Not supported!");
        }

        /**
         * Appends a row count distinct projection to the query string.
         *
         * @param logicalName The alias to the table name
         */
        protected void appendRowCountDistinct(String logicalName) {
            throw new IllegalStateException("Not supported!");
        }

        /**
         * Select all the columns from the entity.
         *
         * @param annotationMetadata The annotation metadata
         * @param persistentEntity   The persistent entity
         * @param tableAlias         The table alias
         */
        protected void selectAllColumns(AnnotationMetadata annotationMetadata, PersistentEntity persistentEntity, String tableAlias) {
            throw new IllegalStateException("Not supported!");
        }

        /**
         * Select all the columns from the entity and the joined entities.
         */
        protected void selectAllColumnsAndJoined() {
            throw new IllegalStateException("Not supported!");
        }

        /**
         * Does nothing but subclasses might override and implement new behavior.
         *
         * @param allPaths          The join paths
         * @param joinAliasOverride The join alias override
         */
        protected void selectAllColumnsFromJoinPaths(Collection<JoinPath> allPaths,
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

        private void appendFunction(String functionName, Expression<?> expression) {
            appendFunction(functionName, List.of(expression));
        }

        private void appendFunction(String functionName, List<Expression<?>> expressions) {
            query.append(functionName)
                .append(OPEN_BRACKET);
            for (Iterator<Expression<?>> iterator = expressions.iterator(); iterator.hasNext(); ) {
                Expression<?> expression = iterator.next();
                appendExpression(expression);
                if (iterator.hasNext()) {
                    query.append(COMMA);
                }
            }
            query.append(CLOSE_BRACKET);
            if (columnAlias != null) {
                query.append(AS_CLAUSE).append(columnAlias);
            }
        }

        private void appendExpression(Expression<?> expression) {
            AbstractSqlLikeQueryBuilder2.this.appendExpression(annotationMetadata, query, queryState, expression, true);
        }

        private QueryPropertyPath findProperty(String propertyPath) {
            return queryState.findProperty(propertyPath);
        }

    }
}
