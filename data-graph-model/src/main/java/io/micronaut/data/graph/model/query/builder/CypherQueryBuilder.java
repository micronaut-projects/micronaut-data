/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.graph.model.query.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.model.query.PathQueryDefinition;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.ISelection;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.DefaultOrder;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.IParameterExpression;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import io.micronaut.data.model.jpa.criteria.impl.expression.BinaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.CastExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.CurrentTemporalExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.FunctionExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.IdExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.SubqueryExpression;
import io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpression;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.DisjunctionPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.ExistsSubqueryPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.InPredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.LikePredicate;
import io.micronaut.data.model.jpa.criteria.impl.predicate.NegatedPredicate;
import io.micronaut.data.model.jpa.criteria.impl.selection.AliasedSelection;
import io.micronaut.data.model.jpa.criteria.impl.selection.CompoundSelection;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.impl.AdvancedPredicateVisitor;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds Cypher queries for graph repositories at compile time.
 *
 * <p>The root node is bound to the variable {@code n}. Criteria on associations add relationship
 * patterns to the {@code MATCH} clause, and fetch joins add an {@code OPTIONAL MATCH} per association
 * whose results are collected into a column named {@code j_<association>}.
 * Parameters are rendered as {@code $p<index>}.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Introspected
public class CypherQueryBuilder implements QueryBuilder {

    /**
     * The variable of the root node.
     */
    public static final String ROOT = "n";
    /**
     * The prefix of a column that holds the collected values of a fetch join.
     */
    public static final String JOIN_COLUMN_PREFIX = "j_";
    /**
     * The key of the relationship in a collected relationship entity map.
     */
    public static final String REL_KEY = "rel";
    /**
     * The key of the target node in a collected relationship entity map.
     */
    public static final String NODE_KEY = "node";
    /**
     * The parameter name prefix.
     */
    public static final String PARAMETER_PREFIX = "p";
    /**
     * The variable of a path.
     */
    public static final String PATH = "p";
    /**
     * The variable of the start node of a path or relationship query.
     */
    public static final String PATH_START = "s";
    /**
     * The variable of the end node of a path or relationship query.
     */
    public static final String PATH_END = "e";
    /**
     * The variable of a relationship in a relationship query.
     */
    public static final String PATH_RELATIONSHIP = "r";
    /**
     * The column holding the start node of a returned relationship.
     */
    public static final String RELATIONSHIP_FROM = "f";
    /**
     * The column holding the end node of a returned relationship.
     */
    public static final String RELATIONSHIP_TO = "t";
    private static final String WITH_DISTINCT = " WITH DISTINCT ";
    private static final String DISTINCT = "DISTINCT ";
    private static final String TO_LOWER = "toLower(";
    private static final String RESULT = "result";
    private static final String CLOSE_AS = ") AS ";
    private static final String RETURN = " RETURN ";
    private static final String AND = " AND ";

    @Override
    public @Nullable QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition definition) {
        // Inserts are created at runtime from the entity and its relationships
        return null;
    }

    @Override
    public QueryResult buildSelect(AnnotationMetadata annotationMetadata, SelectQueryDefinition definition) {
        QueryState state = new QueryState(definition.persistentEntity(), definition.getJoinPaths());
        String where = renderWhere(definition.predicate(), state);

        SelectionRenderer selectionRenderer = new SelectionRenderer(state);
        Selection<?> selection = definition.selection();
        if (selection instanceof ISelection<?> iSelection) {
            iSelection.visitSelection(selectionRenderer);
        } else if (selection != null) {
            throw new IllegalStateException("Unsupported selection type: " + selection.getClass().getName());
        } else {
            selectionRenderer.entity = true;
        }
        List<OrderItem> orders = renderOrders(definition.order(), state);

        StringBuilder query = new StringBuilder();
        appendMatch(query, state, where);
        appendReturn(query, state, selectionRenderer, definition.isDistinct(), orders);
        String orderBy = orderByClause(orders);
        if (!orderBy.isEmpty()) {
            query.append(" ORDER BY ").append(orderBy);
        }
        if (definition.offset() > 0 || definition.limit() > 0) {
            query.append(buildLimitAndOffset(definition.limit(), Math.max(definition.offset(), 0)));
        } else if (selectionRenderer.literal) {
            // An existence check needs a single row
            query.append(" LIMIT 1");
        }
        return QueryResult.of(query.toString(), List.of(query.toString()), state.parameterBindings, state.additionalRequiredParameters);
    }

    private void appendReturn(StringBuilder query, QueryState state, SelectionRenderer selectionRenderer, boolean distinct, List<OrderItem> orders) {
        List<String> carried = List.of();
        if (state.hasPatterns() && (selectionRenderer.entity || selectionRenderer.rootOnly)) {
            carried = appendDistinctRoot(query, orders);
        }
        List<String> joinColumns = selectionRenderer.entity ? appendFetchJoins(query, state, carried) : List.of();
        query.append(RETURN);
        if (distinct) {
            query.append(DISTINCT);
        }
        if (selectionRenderer.entity) {
            query.append(ROOT);
            for (String joinColumn : joinColumns) {
                query.append(", ").append(joinColumn);
            }
        } else {
            query.append(selectionRenderer.out);
        }
    }

    /**
     * Reduces the rows of the relationship patterns added by criteria back to one row per root node.
     * A sort on a property reached through such a pattern is aggregated here, because the pattern
     * variables leave the scope at this point: an ascending order sorts by the smallest value of the
     * related nodes, a descending one by the largest.
     *
     * @param query  The query
     * @param orders The orders of the query
     * @return The aliases of the aggregated order values, which later {@code WITH} clauses must carry
     */
    private static List<String> appendDistinctRoot(StringBuilder query, List<OrderItem> orders) {
        List<String> carried = new ArrayList<>();
        for (OrderItem order : orders) {
            if (order.onAssociation) {
                if (carried.isEmpty()) {
                    query.append(" WITH ").append(ROOT);
                }
                String alias = "o" + carried.size();
                query.append(", ").append(order.ascending ? "min(" : "max(").append(order.expression).append(CLOSE_AS).append(alias);
                order.alias = alias;
                carried.add(alias);
            }
        }
        if (carried.isEmpty()) {
            query.append(WITH_DISTINCT).append(ROOT);
        }
        return carried;
    }

    @Override
    public QueryResult buildUpdate(AnnotationMetadata annotationMetadata, UpdateQueryDefinition definition) {
        QueryState state = new QueryState(definition.persistentEntity(), definition.getJoinPaths());
        String where = renderWhere(definition.predicate(), state);
        StringBuilder sets = new StringBuilder();
        Iterator<Map.Entry<String, Object>> iterator = definition.propertiesToUpdate().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            PersistentProperty property = state.entity.getPropertyByName(entry.getKey());
            if (property == null) {
                property = state.entity.getIdentity();
            }
            if (property == null || !property.getName().equals(entry.getKey())) {
                throw new IllegalArgumentException("Cannot update non-existent property: " + entry.getKey());
            }
            io.micronaut.data.model.PersistentPropertyPath propertyPath = io.micronaut.data.model.PersistentPropertyPath.of(List.of(), property);
            sets.append(ROOT).append('.').append(GraphEntities.escapeName(GraphEntities.propertyName(property))).append(" = ");
            Object value = entry.getValue();
            ExpressionRenderer renderer = new ExpressionRenderer(state, sets);
            if (value instanceof BindingParameter bindingParameter) {
                renderer.appendParameter(bindingParameter, propertyPath, propertyPath);
            } else if (value instanceof Expression<?> expression) {
                renderer.appendExpression(expression, propertyPath);
            } else {
                sets.append(asLiteral(value));
            }
            if (iterator.hasNext()) {
                sets.append(", ");
            }
        }
        StringBuilder query = new StringBuilder();
        appendMatch(query, state, where);
        if (state.hasPatterns()) {
            query.append(WITH_DISTINCT).append(ROOT);
        }
        query.append(" SET ").append(sets).append(" RETURN count(*)");
        return QueryResult.of(query.toString(), List.of(query.toString()), state.parameterBindings, state.additionalRequiredParameters);
    }

    @Override
    public QueryResult buildDelete(AnnotationMetadata annotationMetadata, DeleteQueryDefinition definition) {
        QueryState state = new QueryState(definition.persistentEntity(), definition.getJoinPaths());
        String where = renderWhere(definition.predicate(), state);
        StringBuilder query = new StringBuilder();
        appendMatch(query, state, where);
        if (state.hasPatterns()) {
            query.append(WITH_DISTINCT).append(ROOT);
        }
        query.append(" DETACH DELETE ").append(ROOT).append(" RETURN count(*)");
        return QueryResult.of(query.toString(), List.of(query.toString()), state.parameterBindings, state.additionalRequiredParameters);
    }

    /**
     * Builds a query for paths or relationships between nodes.
     *
     * @param definition The definition
     * @return The query
     */
    public QueryResult buildPathQuery(PathQueryDefinition definition) {
        PersistentEntity entity = definition.entity();
        if (!entity.hasIdentity() && (definition.start() != null || definition.end() != null)) {
            throw new IllegalArgumentException("Entity [" + entity.getName() + "] has no identity to match path endpoints by");
        }
        QueryState state = new QueryState(entity, List.of());
        String label = GraphEntities.labelExpression(entity);
        List<String> conditions = new ArrayList<>();
        if (definition.start() != null) {
            conditions.add(endpointCondition(state, entity, PATH_START, definition.start()));
        }
        if (definition.end() != null) {
            conditions.add(endpointCondition(state, entity, PATH_END, definition.end()));
        }
        String types = relationshipTypes(definition.relationships());
        StringBuilder query = new StringBuilder("MATCH ");
        if (definition.kind() == PathQueryDefinition.Kind.RELATIONSHIPS) {
            appendRelationshipQuery(query, definition, label, types, conditions);
        } else {
            appendPathQuery(query, definition, label, types, conditions);
        }
        return QueryResult.of(query.toString(), List.of(query.toString()), state.parameterBindings, state.additionalRequiredParameters);
    }

    private void appendRelationshipQuery(StringBuilder query, PathQueryDefinition definition, String label, String types, List<String> conditions) {
        Direction direction = definition.direction();
        if (direction == Direction.UNDIRECTED && (definition.start() == null || definition.end() == null)) {
            // Without both endpoints an undirected pattern returns each relationship twice
            direction = Direction.OUTGOING;
        }
        query.append(directed("(" + PATH_START + label + ")", "[" + PATH_RELATIONSHIP + types + "]", "(" + PATH_END + label + ")", direction));
        appendWhere(query, conditions);
        switch (definition.result()) {
            case COUNT -> query.append(" RETURN count(").append(PATH_RELATIONSHIP).append(") AS result");
            case EXISTS -> query.append(" RETURN true AS result LIMIT 1");
            default -> query.append(RETURN).append(PATH_RELATIONSHIP).append(", startNode(").append(PATH_RELATIONSHIP)
                .append(CLOSE_AS).append(RELATIONSHIP_FROM).append(", endNode(").append(PATH_RELATIONSHIP).append(CLOSE_AS).append(RELATIONSHIP_TO);
        }
    }

    private void appendPathQuery(StringBuilder query, PathQueryDefinition definition, String label, String types, List<String> conditions) {
        if (definition.start() != null && definition.end() != null && definition.kind() != PathQueryDefinition.Kind.ALL) {
            conditions.add(PATH_START + " <> " + PATH_END);
        }
        query.append('(').append(PATH_START).append(label).append("), (").append(PATH_END).append(label).append(')');
        appendWhere(query, conditions);
        appendPathMatch(query, definition.kind(), "[" + types + pathDepth(definition) + "]", definition.direction());
        switch (definition.result()) {
            case COUNT -> query.append(" RETURN count(").append(PATH).append(") AS result");
            case EXISTS -> query.append(" RETURN true AS result LIMIT 1");
            default -> {
                query.append(RETURN).append(PATH);
                if (definition.kind() == PathQueryDefinition.Kind.ALL) {
                    query.append(" ORDER BY length(").append(PATH).append(')');
                }
            }
        }
    }

    private static String pathDepth(PathQueryDefinition definition) {
        if (definition.maxDepthBound()) {
            return "*1.." + PathQueryDefinition.PATH_LIMIT_TOKEN;
        }
        if (definition.maxDepth() > 0) {
            return "*1.." + definition.maxDepth();
        }
        return "*";
    }

    /**
     * Appends the clause that matches the paths, binding them to the variable {@code p}.
     *
     * @param query        The query
     * @param kind         The kind of path query
     * @param relationship The variable-length relationship pattern, for example {@code [:KNOWS*1..5]}
     * @param direction    The direction
     */
    protected void appendPathMatch(StringBuilder query, PathQueryDefinition.Kind kind, String relationship, Direction direction) {
        String pattern = directed("(" + PATH_START + ")", relationship, "(" + PATH_END + ")", direction);
        query.append(" MATCH ").append(PATH).append(" = ");
        switch (kind) {
            case SHORTEST -> query.append("shortestPath(").append(pattern).append(')');
            case ALL_SHORTEST -> query.append("allShortestPaths(").append(pattern).append(')');
            default -> query.append(pattern);
        }
    }

    private String endpointCondition(QueryState state, PersistentEntity entity, String variable, BindingParameter parameter) {
        PersistentProperty identity = entity.getIdentity();
        io.micronaut.data.model.PersistentPropertyPath idPath = io.micronaut.data.model.PersistentPropertyPath.of(List.of(), identity);
        int index = state.pushParameter(parameter, BindingParameter.BindingContext.create()
            .incomingMethodParameterProperty(idPath)
            .outgoingQueryParameterProperty(idPath));
        return identityExpression(identity, variable) + " = " + parameterPlaceholder(index);
    }

    private static void appendWhere(StringBuilder query, List<String> conditions) {
        if (!conditions.isEmpty()) {
            query.append(" WHERE ").append(String.join(AND, conditions));
        }
    }

    private static String relationshipTypes(List<Association> associations) {
        if (associations.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (Association association : associations) {
            String type = GraphEntities.escapeName(GraphEntities.relationshipType(association));
            if (seen.add(type)) {
                sb.append(sb.isEmpty() ? ":" : "|").append(type);
            }
        }
        return sb.toString();
    }

    /**
     * Renders a relationship pattern between two node patterns in the given direction.
     *
     * @param from         The pattern of the start node
     * @param relationship The relationship pattern
     * @param to           The pattern of the end node
     * @param direction    The direction
     * @return The pattern
     */
    protected static String directed(String from, String relationship, String to, Direction direction) {
        return switch (direction) {
            case OUTGOING -> from + "-" + relationship + "->" + to;
            case INCOMING -> from + "<-" + relationship + "-" + to;
            case UNDIRECTED -> from + "-" + relationship + "-" + to;
        };
    }

    @Override
    public String buildLimitAndOffset(long limit, long offset) {
        StringBuilder sb = new StringBuilder();
        if (offset > 0) {
            sb.append(" SKIP ").append(offset);
        }
        if (limit > 0) {
            sb.append(" LIMIT ").append(limit);
        }
        return sb.toString();
    }

    /**
     * Renders the expression that reads the identifier of a graph element.
     *
     * @param identity The identity property
     * @param variable The variable of the graph element
     * @return The expression
     */
    protected String identityExpression(PersistentProperty identity, String variable) {
        if (GraphEntities.isNativeId(identity)) {
            String typeName = identity.getTypeName();
            if (typeName.equals(String.class.getName())) {
                return "elementId(" + variable + ")";
            }
            return "id(" + variable + ")";
        }
        return variable + "." + GraphEntities.escapeName(GraphEntities.propertyName(identity));
    }

    /**
     * Renders a parameter placeholder.
     *
     * @param index The parameter index
     * @return The placeholder
     */
    protected String parameterPlaceholder(int index) {
        return "$" + PARAMETER_PREFIX + index;
    }

    /**
     * Renders the expression that collects the fetched values of a relationship entity association.
     *
     * @param relVariable  The variable of the relationship
     * @param nodeVariable The variable of the target node
     * @return The expression
     */
    protected String relationshipEntityCollectExpression(String relVariable, String nodeVariable) {
        return "collect(CASE WHEN " + relVariable + " IS NULL THEN NULL ELSE {" + REL_KEY + ": " + relVariable + ", " + NODE_KEY + ": " + nodeVariable + "} END)";
    }

    private void appendMatch(StringBuilder query, QueryState state, @Nullable String where) {
        query.append("MATCH (").append(ROOT).append(GraphEntities.labelExpression(state.entity)).append(')');
        for (Pattern pattern : state.patterns.values()) {
            query.append(", ").append(pattern.text);
        }
        if (where != null && !where.isEmpty()) {
            query.append(" WHERE ").append(where);
        }
    }

    private List<String> appendFetchJoins(StringBuilder query, QueryState state, List<String> carried) {
        List<String> columns = new ArrayList<>();
        for (JoinPath joinPath : state.joinPaths) {
            if (joinPath.getJoinType().isFetch()) {
                appendFetchJoin(query, joinPath, columns, carried);
            }
        }
        return columns;
    }

    private void appendFetchJoin(StringBuilder query, JoinPath joinPath, List<String> columns, List<String> carried) {
        Association[] associationPath = joinPath.getAssociationPath();
        Association association = associationPath[0];
        boolean relationshipEntity = GraphEntities.isRelationshipEntity(association.getAssociatedEntity());
        if (associationPath.length > 2 || (associationPath.length == 2 && !(relationshipEntity && isTargetNode(associationPath[1])))) {
            throw new IllegalArgumentException("Graph repositories can only join direct relationships, found join path: " + joinPath.getPath());
        }
        if (!GraphEntities.isRelationship(association)) {
            throw new IllegalArgumentException("Association [" + association.getName() + "] is not a @Relationship and cannot be joined");
        }
        String column = JOIN_COLUMN_PREFIX + association.getName();
        if (!columns.contains(column)) {
            int index = columns.size();
            String nodeVariable = "j" + index;
            PersistentEntity target = GraphEntities.targetNodeEntity(association);
            String nodePattern = "(" + nodeVariable + GraphEntities.labelExpression(target) + ")";
            query.append(" OPTIONAL MATCH ");
            String collect;
            if (relationshipEntity) {
                String relVariable = "r" + index;
                query.append(GraphEntities.relationshipPattern(association, ROOT, relVariable, nodePattern));
                collect = relationshipEntityCollectExpression(relVariable, nodeVariable);
            } else {
                query.append(GraphEntities.relationshipPattern(association, ROOT, null, nodePattern));
                collect = "collect(" + nodeVariable + ")";
            }
            query.append(" WITH ").append(ROOT);
            for (String alias : carried) {
                query.append(", ").append(alias);
            }
            for (String previous : columns) {
                query.append(", ").append(previous);
            }
            query.append(", ").append(collect).append(" AS ").append(column);
            columns.add(column);
        }
    }

    private static boolean isTargetNode(Association association) {
        return association.getAnnotationMetadata().hasStereotype("io.micronaut.data.graph.annotation.TargetNode");
    }

    private @Nullable String renderWhere(@Nullable Predicate predicate, QueryState state) {
        if (predicate == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (predicate instanceof IPredicate iPredicate) {
            iPredicate.visitPredicate(new PredicateRenderer(state, sb));
        } else {
            throw new IllegalStateException("Unsupported predicate type: " + predicate.getClass().getName());
        }
        return sb.toString();
    }

    private List<OrderItem> renderOrders(List<Order> orders, QueryState state) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<OrderItem> items = new ArrayList<>(orders.size());
        for (Order order : orders) {
            StringBuilder expression = new StringBuilder();
            ExpressionRenderer renderer = new ExpressionRenderer(state, expression);
            boolean ignoreCase = order instanceof DefaultOrder<?> defaultOrder && defaultOrder.isIgnoreCase();
            Nulls nulls = order instanceof DefaultOrder<?> defaultOrder ? defaultOrder.getNullPrecedence() : Nulls.NONE;
            if (ignoreCase) {
                expression.append(TO_LOWER);
                renderer.appendExpression(order.getExpression(), null);
                expression.append(')');
            } else {
                renderer.appendExpression(order.getExpression(), null);
            }
            boolean onAssociation = order.getExpression() instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> propertyPath
                && !propertyPath.getPropertyPath().getAssociations().isEmpty();
            items.add(new OrderItem(expression.toString(), order.isAscending(), nulls, onAssociation));
        }
        return items;
    }

    private static String orderByClause(List<OrderItem> orders) {
        StringBuilder sb = new StringBuilder();
        for (OrderItem order : orders) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            String expression = order.alias != null ? order.alias : order.expression;
            switch (order.nulls) {
                case FIRST -> sb.append("CASE WHEN ").append(expression).append(" IS NULL THEN 0 ELSE 1 END, ");
                case LAST -> sb.append("CASE WHEN ").append(expression).append(" IS NULL THEN 1 ELSE 0 END, ");
                default -> {
                    // No null precedence
                }
            }
            sb.append(expression).append(order.ascending ? " ASC" : " DESC");
        }
        return sb.toString();
    }

    /**
     * Renders a literal value.
     *
     * @param value The value
     * @return The Cypher literal
     */
    protected String asLiteral(@Nullable Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Enum<?> e) {
            return quote(e.name());
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder sb = new StringBuilder("[");
            Iterator<?> iterator = collection.iterator();
            while (iterator.hasNext()) {
                sb.append(asLiteral(iterator.next()));
                if (iterator.hasNext()) {
                    sb.append(", ");
                }
            }
            return sb.append(']').toString();
        }
        return quote(value.toString());
    }

    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /**
     * A relationship pattern added to the {@code MATCH} clause by a criterion on an association.
     *
     * @param text         The pattern
     * @param nodeVariable The variable of the target node
     * @param relVariable  The variable of the relationship, or null if it has none
     */
    private record Pattern(String text, String nodeVariable, @Nullable String relVariable) {
    }

    /**
     * The state of a query being built.
     */
    private final class QueryState {
        private final PersistentEntity entity;
        private final Collection<JoinPath> joinPaths;
        private final Map<String, Pattern> patterns = new LinkedHashMap<>();
        private final List<QueryParameterBinding> parameterBindings = new ArrayList<>();
        private final Map<String, String> additionalRequiredParameters = new LinkedHashMap<>();
        private int position;

        private QueryState(PersistentEntity entity, Collection<JoinPath> joinPaths) {
            this.entity = entity;
            this.joinPaths = joinPaths == null ? List.of() : joinPaths;
        }

        private boolean hasPatterns() {
            return !patterns.isEmpty();
        }

        private int pushParameter(BindingParameter bindingParameter, BindingParameter.BindingContext bindingContext) {
            int index = position++;
            parameterBindings.add(bindingParameter.bind(bindingContext.index(index).name(PARAMETER_PREFIX + index)));
            return index;
        }

        /**
         * Resolves the expression of a property path, adding relationship patterns for associations.
         *
         * @param propertyPath The property path
         * @return The expression
         */
        private String resolve(io.micronaut.data.model.PersistentPropertyPath propertyPath) {
            return propertyExpression(propertyPath, resolveVariable(propertyPath.getAssociations()));
        }

        /**
         * Resolves the variable of the graph element at the end of a chain of associations,
         * adding relationship patterns for the associations.
         *
         * @param associations The associations
         * @return The variable
         */
        private String resolveVariable(List<Association> associations) {
            String variable = ROOT;
            Pattern relationshipEntityPattern = null;
            StringBuilder path = new StringBuilder();
            for (Association association : associations) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(association.getName());
                if (relationshipEntityPattern != null && isTargetNode(association)) {
                    variable = relationshipEntityPattern.nodeVariable();
                    relationshipEntityPattern = null;
                    continue;
                }
                if (association.isEmbedded()) {
                    throw new IllegalArgumentException("Embedded properties are not supported by graph repositories: " + path);
                }
                if (!GraphEntities.isRelationship(association)) {
                    throw new IllegalArgumentException("Association [" + association.getName() + "] is not a @Relationship and cannot be queried");
                }
                String from = variable;
                Pattern pattern = patterns.computeIfAbsent(path.toString(), key -> newPattern(association, from));
                if (pattern.relVariable() != null) {
                    variable = pattern.relVariable();
                    relationshipEntityPattern = pattern;
                } else {
                    variable = pattern.nodeVariable();
                }
            }
            return variable;
        }

        private Pattern newPattern(Association association, String from) {
            int index = patterns.size();
            String nodeVariable = "a" + index;
            PersistentEntity target = GraphEntities.targetNodeEntity(association);
            String nodePattern = "(" + nodeVariable + GraphEntities.labelExpression(target) + ")";
            if (GraphEntities.isRelationshipEntity(association.getAssociatedEntity())) {
                String relVariable = "ar" + index;
                return new Pattern(GraphEntities.relationshipPattern(association, from, relVariable, nodePattern), nodeVariable, relVariable);
            }
            return new Pattern(GraphEntities.relationshipPattern(association, from, null, nodePattern), nodeVariable, null);
        }

        private String propertyExpression(io.micronaut.data.model.PersistentPropertyPath propertyPath, String variable) {
            PersistentProperty property = propertyPath.getProperty();
            PersistentEntity owner = property.getOwner();
            if (owner.hasIdentity() && owner.getIdentity().getName().equals(property.getName())) {
                return identityExpression(property, variable);
            }
            if (property instanceof Association association && !association.isEmbedded()) {
                // A criterion on an association itself compares its identifier
                PersistentEntity target = GraphEntities.targetNodeEntity(association);
                io.micronaut.data.model.PersistentPropertyPath idPath = io.micronaut.data.model.PersistentPropertyPath.of(
                    concat(propertyPath.getAssociations(), association), target.getIdentity());
                return resolve(idPath);
            }
            return variable + "." + GraphEntities.escapeName(GraphEntities.propertyName(property));
        }

        private static List<Association> concat(List<Association> associations, Association association) {
            List<Association> list = new ArrayList<>(associations);
            list.add(association);
            return list;
        }
    }

    /**
     * Renders expressions.
     */
    private class ExpressionRenderer implements ExpressionVisitor {
        final QueryState state;
        final StringBuilder out;
        int depth;
        private io.micronaut.data.model.@Nullable PersistentPropertyPath boundProperty;

        private ExpressionRenderer(QueryState state, StringBuilder out) {
            this.state = state;
            this.out = out;
        }

        void appendExpression(Expression<?> expression, io.micronaut.data.model.@Nullable PersistentPropertyPath boundProperty) {
            io.micronaut.data.model.PersistentPropertyPath previous = this.boundProperty;
            this.boundProperty = boundProperty;
            depth++;
            try {
                if (expression instanceof BindingParameter bindingParameter) {
                    appendParameter(bindingParameter, boundProperty, boundProperty);
                } else if (expression instanceof IExpression<?> iExpression) {
                    iExpression.visitExpression(this);
                } else {
                    throw new IllegalStateException("Unsupported expression: " + expression);
                }
            } finally {
                depth--;
                this.boundProperty = previous;
            }
        }

        void appendParameter(BindingParameter bindingParameter,
                             io.micronaut.data.model.@Nullable PersistentPropertyPath in,
                             io.micronaut.data.model.@Nullable PersistentPropertyPath out) {
            int index = state.pushParameter(bindingParameter, BindingParameter.BindingContext.create()
                .incomingMethodParameterProperty(in)
                .outgoingQueryParameterProperty(out));
            this.out.append(parameterPlaceholder(index));
        }

        @Override
        public void visit(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            out.append(state.resolve(persistentPropertyPath.getPropertyPath()));
        }

        @Override
        public void visit(PersistentEntityRoot<?> entityRoot) {
            out.append(ROOT);
        }

        @Override
        public void visit(PersistentEntitySubquery<?> subquery) {
            throw new UnsupportedOperationException("Subqueries are not supported by graph repositories");
        }

        @Override
        public void visit(LiteralExpression<?> literalExpression) {
            out.append(asLiteral(literalExpression.getValue()));
        }

        @Override
        public void visit(UnaryExpression<?> unaryExpression) {
            Expression<?> expression = unaryExpression.getExpression();
            switch (unaryExpression.getType()) {
                case AVG -> appendFunction("avg", expression);
                case SUM -> appendFunction("sum", expression);
                case MAX -> appendFunction("max", expression);
                case MIN -> appendFunction("min", expression);
                case UPPER -> appendFunction("toUpper", expression);
                case LOWER -> appendFunction("toLower", expression);
                case LENGTH -> appendFunction("size", expression);
                case COUNT -> {
                    out.append("count(");
                    if (expression instanceof PersistentEntityRoot<?> && state.hasPatterns()) {
                        out.append(DISTINCT);
                    }
                    appendExpression(expression, null);
                    out.append(')');
                }
                case COUNT_DISTINCT -> {
                    out.append("count(DISTINCT ");
                    appendExpression(expression, null);
                    out.append(')');
                }
                default -> throw new UnsupportedOperationException("Unsupported expression type: " + unaryExpression.getType());
            }
        }

        private void appendFunction(String name, Expression<?> expression) {
            out.append(name).append('(');
            appendExpression(expression, boundProperty);
            out.append(')');
        }

        @Override
        public void visit(BinaryExpression<?> binaryExpression) {
            io.micronaut.data.model.PersistentPropertyPath bound = boundProperty;
            if (bound == null) {
                bound = findPropertyPath(binaryExpression.getLeft());
                if (bound == null) {
                    bound = findPropertyPath(binaryExpression.getRight());
                }
            }
            out.append('(');
            appendExpression(binaryExpression.getLeft(), bound);
            out.append(switch (binaryExpression.getType()) {
                case SUM, CONCAT -> " + ";
                case PROD -> " * ";
                case QUOT -> " / ";
                default -> throw new UnsupportedOperationException("Unsupported binary expression: " + binaryExpression.getType());
            });
            appendExpression(binaryExpression.getRight(), bound);
            out.append(')');
        }

        @Override
        public void visit(IdExpression<?, ?> idExpression) {
            PersistentEntity entity = state.entity;
            if (!entity.hasIdentity()) {
                throw new IllegalStateException("Entity [" + entity.getName() + "] has no identity");
            }
            out.append(identityExpression(entity.getIdentity(), ROOT));
        }

        @Override
        public void visit(FunctionExpression<?> functionExpression) {
            out.append(functionExpression.getName()).append('(');
            Iterator<Expression<?>> iterator = functionExpression.getExpressions().iterator();
            while (iterator.hasNext()) {
                appendExpression(iterator.next(), boundProperty);
                if (iterator.hasNext()) {
                    out.append(", ");
                }
            }
            out.append(')');
        }

        @Override
        public void visit(IParameterExpression<?> parameterExpression) {
            appendParameter(parameterExpression, boundProperty, boundProperty);
        }

        @Override
        public void visit(SubqueryExpression<?> subqueryExpression) {
            throw new UnsupportedOperationException("Subqueries are not supported by graph repositories");
        }

        @Override
        public void visit(CastExpression<?> castExpression) {
            Class<?> type = castExpression.getJavaType();
            String function;
            if (type == String.class) {
                function = "toString";
            } else if (type == Integer.class || type == Long.class || type == Short.class || type == int.class || type == long.class) {
                function = "toInteger";
            } else if (type == Double.class || type == Float.class || type == double.class || type == float.class) {
                function = "toFloat";
            } else if (type == Boolean.class || type == boolean.class) {
                function = "toBoolean";
            } else {
                throw new UnsupportedOperationException("Unsupported cast to: " + type.getName());
            }
            appendFunction(function, castExpression.getExpression());
        }

        @Override
        public void visit(CurrentTemporalExpression<?> currentTemporalExpression) {
            out.append(switch (currentTemporalExpression.getType()) {
                case DATE -> "date()";
                case TIME -> "localtime()";
                case TIMESTAMP -> "datetime()";
            });
        }

        io.micronaut.data.model.@Nullable PersistentPropertyPath findPropertyPath(@Nullable Expression<?> expression) {
            if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> path) {
                return path.getPropertyPath();
            }
            if (expression instanceof UnaryExpression<?> unaryExpression) {
                return findPropertyPath(unaryExpression.getExpression());
            }
            if (expression instanceof BinaryExpression<?> binaryExpression) {
                io.micronaut.data.model.PersistentPropertyPath left = findPropertyPath(binaryExpression.getLeft());
                return left != null ? left : findPropertyPath(binaryExpression.getRight());
            }
            return null;
        }
    }

    /**
     * Renders predicates.
     */
    private final class PredicateRenderer extends ExpressionRenderer implements AdvancedPredicateVisitor<io.micronaut.data.model.PersistentPropertyPath> {

        private PredicateRenderer(QueryState state, StringBuilder out) {
            super(state, out);
        }

        @Override
        public io.micronaut.data.model.PersistentPropertyPath getRequiredProperty(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            return persistentPropertyPath.getPropertyPath();
        }

        private void visitBoolean(IExpression<Boolean> expression) {
            if (expression instanceof IPredicate predicate) {
                predicate.visitPredicate(this);
            } else if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?>) {
                visitIsTrue(expression);
            } else {
                throw new IllegalStateException("Unknown boolean expression: " + expression);
            }
        }

        private void visitJunction(Collection<? extends IExpression<Boolean>> predicates, String operator, String empty) {
            if (predicates.isEmpty()) {
                out.append(empty);
                return;
            }
            if (predicates.size() == 1) {
                visitBoolean(predicates.iterator().next());
                return;
            }
            out.append('(');
            Iterator<? extends IExpression<Boolean>> iterator = predicates.iterator();
            while (iterator.hasNext()) {
                visitBoolean(iterator.next());
                if (iterator.hasNext()) {
                    out.append(operator);
                }
            }
            out.append(')');
        }

        @Override
        public void visit(ConjunctionPredicate conjunction) {
            visitJunction(conjunction.getPredicates(), AND, "true");
        }

        @Override
        public void visit(DisjunctionPredicate disjunction) {
            visitJunction(disjunction.getPredicates(), " OR ", "false");
        }

        @Override
        public void visit(NegatedPredicate negate) {
            out.append("NOT (");
            visitBoolean(negate.getNegated());
            out.append(')');
        }

        @Override
        public void visit(InPredicate<?> inPredicate) {
            visitIn(inPredicate.getExpression(), inPredicate.getValues(), false);
        }

        @Override
        public void visitIn(Expression<?> expression, Collection<?> values, boolean negated) {
            io.micronaut.data.model.PersistentPropertyPath bound = findPropertyPath(expression);
            if (negated) {
                out.append("NOT (");
            }
            appendExpression(expression, bound);
            out.append(" IN ");
            if (values.size() == 1 && values.iterator().next() instanceof BindingParameter bindingParameter) {
                appendParameter(bindingParameter, bound, bound);
            } else {
                out.append('[');
                Iterator<?> iterator = values.iterator();
                while (iterator.hasNext()) {
                    Object value = iterator.next();
                    if (value instanceof Expression<?> e) {
                        appendExpression(e, bound);
                    } else {
                        out.append(asLiteral(value));
                    }
                    if (iterator.hasNext()) {
                        out.append(", ");
                    }
                }
                out.append(']');
            }
            if (negated) {
                out.append(')');
            }
        }

        private void appendComparison(Expression<?> left, String operator, Expression<?> right, boolean ignoreCase) {
            io.micronaut.data.model.PersistentPropertyPath bound = findPropertyPath(left);
            if (bound == null) {
                bound = findPropertyPath(right);
            }
            if (ignoreCase) {
                out.append(TO_LOWER);
                appendExpression(left, bound);
                out.append(')').append(operator).append(TO_LOWER);
                appendExpression(right, bound);
                out.append(')');
            } else {
                appendExpression(left, bound);
                out.append(operator);
                appendExpression(right, bound);
            }
        }

        @Override
        public void visitIdEquals(Expression<?> expression) {
            PersistentEntity entity = state.entity;
            if (!entity.hasIdentity()) {
                throw new IllegalStateException("Entity [" + entity.getName() + "] has no identity");
            }
            io.micronaut.data.model.PersistentPropertyPath idPath = io.micronaut.data.model.PersistentPropertyPath.of(List.of(), entity.getIdentity());
            out.append(identityExpression(entity.getIdentity(), ROOT)).append(" = ");
            appendExpression(expression, idPath);
        }

        @Override
        public void visitEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            appendComparison(leftExpression, " = ", rightExpression, ignoreCase);
        }

        @Override
        public void visitNotEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            appendComparison(leftExpression, " <> ", rightExpression, ignoreCase);
        }

        @Override
        public void visitGreaterThan(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendComparison(leftExpression, " > ", rightExpression, false);
        }

        @Override
        public void visitGreaterThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendComparison(leftExpression, " >= ", rightExpression, false);
        }

        @Override
        public void visitLessThan(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendComparison(leftExpression, " < ", rightExpression, false);
        }

        @Override
        public void visitLessThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression) {
            appendComparison(leftExpression, " <= ", rightExpression, false);
        }

        @Override
        public void visitStartsWith(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase) {
            appendComparison(leftExpression, " STARTS WITH ", expression, ignoreCase);
        }

        @Override
        public void visitEndsWith(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase) {
            appendComparison(leftExpression, " ENDS WITH ", expression, ignoreCase);
        }

        @Override
        public void visitContains(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase) {
            appendComparison(leftExpression, " CONTAINS ", expression, ignoreCase);
        }

        @Override
        public void visitRegexp(Expression<?> leftExpression, Expression<?> expression) {
            appendComparison(leftExpression, " =~ ", expression, false);
        }

        @Override
        public void visitArrayContains(Expression<?> leftExpression, Expression<?> expression) {
            io.micronaut.data.model.PersistentPropertyPath bound = findPropertyPath(leftExpression);
            appendExpression(expression, bound);
            out.append(" IN ");
            appendExpression(leftExpression, bound);
        }

        @Override
        public void visitInBetween(Expression<?> value, Expression<?> from, Expression<?> to, boolean negated) {
            if (negated) {
                out.append("NOT ");
            }
            out.append('(');
            appendComparison(value, " >= ", from, false);
            out.append(AND);
            appendComparison(value, " <= ", to, false);
            out.append(')');
        }

        @Override
        public void visitIsNull(Expression<?> expression) {
            appendExpression(expression, null);
            out.append(" IS NULL");
        }

        @Override
        public void visitIsNotNull(Expression<?> expression) {
            appendExpression(expression, null);
            out.append(" IS NOT NULL");
        }

        @Override
        public void visitIsTrue(Expression<?> expression) {
            appendExpression(expression, null);
            out.append(" = true");
        }

        @Override
        public void visitIsFalse(Expression<?> expression) {
            appendExpression(expression, null);
            out.append(" = false");
        }

        @Override
        public void visitIsEmpty(Expression<?> expression) {
            String pattern = relationshipExistsPattern(expression);
            if (pattern != null) {
                out.append("NOT ").append(pattern);
                return;
            }
            StringBuilder e = new StringBuilder();
            new ExpressionRenderer(state, e).appendExpression(expression, null);
            out.append('(').append(e).append(" IS NULL OR size(").append(e).append(") = 0)");
        }

        @Override
        public void visitIsNotEmpty(Expression<?> expression) {
            String pattern = relationshipExistsPattern(expression);
            if (pattern != null) {
                out.append(pattern);
                return;
            }
            StringBuilder e = new StringBuilder();
            new ExpressionRenderer(state, e).appendExpression(expression, null);
            out.append('(').append(e).append(" IS NOT NULL AND size(").append(e).append(") > 0)");
        }

        /**
         * An emptiness check on a relationship asks whether the node has any such relationship,
         * which is a pattern predicate rather than a property check.
         *
         * @param expression The expression
         * @return The pattern, or null if the expression is not a relationship
         */
        private @Nullable String relationshipExistsPattern(Expression<?> expression) {
            if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
                io.micronaut.data.model.PersistentPropertyPath propertyPath = persistentPropertyPath.getPropertyPath();
                if (propertyPath.getProperty() instanceof Association association
                    && !association.isEmbedded()
                    && GraphEntities.isRelationship(association)) {
                    String variable = state.resolveVariable(propertyPath.getAssociations());
                    return GraphEntities.relationshipPattern(association, variable, null, "()");
                }
            }
            return null;
        }

        @Override
        public void visit(LikePredicate likePredicate) {
            throw new UnsupportedOperationException("LIKE is not supported by graph repositories. Use StartsWith, EndsWith, Contains or Regex instead.");
        }

        @Override
        public void visit(ExistsSubqueryPredicate existsSubqueryPredicate) {
            throw new UnsupportedOperationException("Subqueries are not supported by graph repositories");
        }
    }

    /**
     * Renders the {@code RETURN} items of a projection.
     */
    private final class SelectionRenderer extends ExpressionRenderer implements SelectionVisitor {
        private boolean entity;
        private boolean literal;
        private boolean rootOnly = true;
        private @Nullable String alias;

        private SelectionRenderer(QueryState state) {
            super(state, new StringBuilder());
        }

        @Override
        public void visit(AliasedSelection<?> aliasedSelection) {
            String previous = alias;
            alias = aliasedSelection.getAlias();
            aliasedSelection.getSelection().visitSelection(this);
            alias = previous;
        }

        @Override
        public void visit(CompoundSelection<?> compoundSelection) {
            Iterator<Selection<?>> iterator = compoundSelection.getCompoundSelectionItems().iterator();
            while (iterator.hasNext()) {
                Selection<?> selection = iterator.next();
                if (selection instanceof ISelection<?> iSelection) {
                    iSelection.visitSelection(this);
                } else {
                    throw new IllegalStateException("Unknown selection: " + selection);
                }
                if (iterator.hasNext()) {
                    out.append(", ");
                }
            }
        }

        @Override
        public void visit(PersistentEntityRoot<?> entityRoot) {
            if (depth == 0) {
                entity = true;
            } else {
                super.visit(entityRoot);
            }
        }

        @Override
        public void visit(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            io.micronaut.data.model.PersistentPropertyPath propertyPath = persistentPropertyPath.getPropertyPath();
            if (!propertyPath.getAssociations().isEmpty()) {
                rootOnly = false;
            }
            super.visit(persistentPropertyPath);
            if (depth == 0) {
                out.append(" AS ").append(GraphEntities.escapeName(alias != null ? alias : propertyPath.getProperty().getName()));
            }
        }

        @Override
        public void visit(UnaryExpression<?> unaryExpression) {
            super.visit(unaryExpression);
            if (depth == 0) {
                appendAlias(RESULT);
            }
        }

        @Override
        public void visit(IdExpression<?, ?> idExpression) {
            super.visit(idExpression);
            if (depth == 0) {
                appendAlias(state.entity.getIdentity().getName());
            }
        }

        @Override
        public void visit(LiteralExpression<?> literalExpression) {
            super.visit(literalExpression);
            if (depth == 0) {
                literal = true;
                appendAlias(RESULT);
            }
        }

        @Override
        public void visit(BinaryExpression<?> binaryExpression) {
            super.visit(binaryExpression);
            if (depth == 0) {
                appendAlias(RESULT);
            }
        }

        @Override
        public void visit(FunctionExpression<?> functionExpression) {
            super.visit(functionExpression);
            if (depth == 0) {
                appendAlias(RESULT);
            }
        }

        private void appendAlias(String defaultAlias) {
            out.append(" AS ").append(GraphEntities.escapeName(alias != null ? alias : defaultAlias));
        }
    }

    /**
     * A rendered {@code ORDER BY} item.
     */
    private static final class OrderItem {
        private final String expression;
        private final boolean ascending;
        private final Nulls nulls;
        private final boolean onAssociation;
        private @Nullable String alias;

        private OrderItem(String expression, boolean ascending, Nulls nulls, boolean onAssociation) {
            this.expression = expression;
            this.ascending = ascending;
            this.nulls = nulls;
            this.onAssociation = onAssociation;
        }
    }
}
