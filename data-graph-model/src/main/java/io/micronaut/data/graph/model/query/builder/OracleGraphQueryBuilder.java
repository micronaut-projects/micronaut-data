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
import io.micronaut.data.annotation.Join;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.DynamicAttributes;
import io.micronaut.data.graph.annotation.DynamicRelationships;
import io.micronaut.data.graph.annotation.Labels;
import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.graph.annotation.TargetNode;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.model.query.PathQueryDefinition;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.IExpression;
import io.micronaut.data.model.jpa.criteria.IPredicate;
import io.micronaut.data.model.jpa.criteria.ISelection;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.PersistentEntitySubquery;
import io.micronaut.data.model.jpa.criteria.impl.DefaultOrder;
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
import io.micronaut.data.model.query.builder.GeneratedEntityUpdateQueryDefinition;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.impl.AdvancedPredicateVisitor;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds queries for Oracle property graphs, which are defined over tables with {@code CREATE PROPERTY GRAPH}.
 * Queries are compiled either to PGQL, run through the PGQL JDBC driver, or to SQL/PGQ {@code GRAPH_TABLE} queries.
 * The graph and the language are set with {@link PropertyGraph} on the repository.
 *
 * <p>Every returned column is aliased {@code "<variable>__<property>"} so that the runtime can rebuild the nodes,
 * with {@code ID_} and {@code LABEL_} holding the identifier and label of a node. Writes are SQL on the vertex
 * tables, and relationships are written to their edge tables with a JDBC repository.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Introspected
public class OracleGraphQueryBuilder implements GraphQueryBuilder {

    /**
     * Separates the variable from the property in a column alias.
     */
    public static final String SEPARATOR = "__";
    /**
     * The suffix of the column holding the identifier of a node.
     */
    public static final String ID_COLUMN = "ID_";
    /**
     * The suffix of the column holding the label of a node.
     */
    public static final String LABEL_COLUMN = "LABEL_";
    /**
     * The suffix of the column holding the schemaless properties of a node, stored as a JSON object.
     */
    public static final String DYNAMIC_ATTRIBUTES_COLUMN = "DYN_";
    /**
     * The suffix of the column holding the type of a relationship.
     */
    public static final String TYPE_COLUMN = "TYPE_";
    /**
     * The suffix of the column saying whether an undirected relationship was matched starting from its source, holding 1 or 0.
     */
    public static final String FORWARD_COLUMN = "FORWARD_";
    /**
     * The column holding the number of the match of a path step.
     */
    public static final String PATH_MATCH_COLUMN = "p" + SEPARATOR + "M_";
    /**
     * The column holding the position of a path step.
     */
    public static final String PATH_STEP_COLUMN = "p" + SEPARATOR + "I_";
    /**
     * The variable of the node a path step starts at.
     */
    public static final String STEP_START = "v1";
    /**
     * The variable of the node a path step ends at.
     */
    public static final String STEP_END = "v2";

    private static final String ROOT = "n";
    private static final String START = "s";
    private static final String END = "t";
    private static final String EDGE = "x";
    private static final String PATH_EDGE = "e";
    private static final String RESULT = "result";
    private static final String COUNT_AS = "COUNT(*) AS ";
    private static final String LOWER = "LOWER(";
    private static final String AND = " AND ";
    private static final String READ_ONLY = "Oracle property graphs are views over tables: nodes are written to their vertex tables with SQL, "
        + "and relationships to their edge tables with a JDBC repository";

    /**
     * Builds the SQL that writes the vertex tables of nodes, as Micronaut Data JDBC does.
     */
    private final SqlQueryBuilder sqlQueryBuilder = new SqlQueryBuilder(Dialect.ORACLE);

    private final String graph;
    private final PropertyGraph.QueryLanguage language;

    /**
     * @param repositoryMetadata The annotation metadata of the repository
     */
    public OracleGraphQueryBuilder(AnnotationMetadata repositoryMetadata) {
        this.graph = graphName(repositoryMetadata.stringValue(PropertyGraph.class).filter(s -> !s.isEmpty()).orElse("graph"));
        this.language = repositoryMetadata.enumValue(PropertyGraph.class, "language", PropertyGraph.QueryLanguage.class)
            .orElse(PropertyGraph.QueryLanguage.SQL_PGQ);
    }

    /**
     * @return The query language
     */
    public PropertyGraph.QueryLanguage getLanguage() {
        return language;
    }

    private boolean sqlPgq() {
        return language == PropertyGraph.QueryLanguage.SQL_PGQ;
    }

    @Override
    public boolean usesDollarParameters() {
        return false;
    }

    @Override
    public @Nullable QueryResult buildInsert(AnnotationMetadata repositoryMetadata, InsertQueryDefinition definition) {
        PersistentEntity entity = definition.persistentEntity();
        if (definition.returning()) {
            checkReturnsEntity(entity);
        }
        return sqlQueryBuilder.buildInsert(repositoryMetadata, new VertexInsert(OracleVertexTableEntity.of(entity), definition.returning()));
    }

    @Override
    public QueryResult buildUpdate(AnnotationMetadata annotationMetadata, UpdateQueryDefinition definition) {
        PersistentEntity entity = definition.persistentEntity();
        if (definition.returningSelection() instanceof PersistentEntityRoot<?>) {
            checkReturnsEntity(entity);
        }
        boolean generated = definition instanceof GeneratedEntityUpdateQueryDefinition generatedUpdate && generatedUpdate.isGeneratedEntityUpdate();
        Map<String, Object> columns = new LinkedHashMap<>();
        definition.propertiesToUpdate().forEach((path, value) -> {
            int dot = path.indexOf('.');
            String name = dot == -1 ? path : path.substring(0, dot);
            PersistentProperty property = entity.getPropertyByName(name);
            if (property == null || !GraphEntities.isSingleRelationship(property)) {
                columns.put(path, value);
            } else if (!generated) {
                throw new IllegalArgumentException("Cannot update the relationship [" + name + "] of [" + entity.getName() + "]: " + READ_ONLY);
            }
        });
        return sqlQueryBuilder.buildUpdate(annotationMetadata, new VertexUpdate(definition, OracleVertexTableEntity.of(entity), columns, generated));
    }

    @Override
    public QueryResult buildDelete(AnnotationMetadata annotationMetadata, DeleteQueryDefinition definition) {
        PersistentEntity entity = definition.persistentEntity();
        if (definition.returningSelection() instanceof PersistentEntityRoot<?>) {
            checkReturnsEntity(entity);
        }
        return sqlQueryBuilder.buildDelete(annotationMetadata, new VertexDelete(definition, OracleVertexTableEntity.of(entity)));
    }

    /**
     * The result mapper of SQL writes reads a single relationship from a foreign key column, which a vertex table does not have.
     */
    private static void checkReturnsEntity(PersistentEntity entity) {
        if (OracleVertexTableEntity.of(entity) != entity) {
            throw new IllegalArgumentException("Cannot return the entity [" + entity.getName() + "] from a write: "
                + "its single relationship has no column in the vertex table. Return a property instead");
        }
    }

    /**
     * Fails a write of the row of a node that holds relationships: a single relationship that is set, or a collection,
     * map, iterable or array of relationships that is not empty, including a
     * {@link DynamicRelationships} map. The write would lose them, as each relationship is a row of an edge table.
     * A null or empty relationship property is allowed: a relationship read from the graph is never filled, as queries
     * return node columns only, so a node read from the graph can be written again.
     *
     * @param entity The entity
     * @param instance The node
     * @param <T> The type of the entity
     */
    @SuppressWarnings("unchecked")
    public static <T> void checkVertexWrite(RuntimePersistentEntity<T> entity, Object instance) {
        for (RuntimePersistentProperty<T> property : entity.getPersistentProperties()) {
            boolean relationship = property instanceof Association association && GraphEntities.isRelationship(association);
            boolean dynamic = property.getAnnotationMetadata().hasAnnotation(DynamicRelationships.class);
            if (!relationship && !dynamic) {
                continue;
            }
            Object value = property.getProperty().get((T) instance);
            if (value == null) {
                continue;
            }
            if (GraphEntities.isSingleRelationship(property)) {
                throw new DataAccessException("Cannot write [" + entity.getName() + "] with its relationship [" + property.getName() + "] set, it would be lost: "
                    + "write the relationship to its edge table with a JDBC repository and clear the property");
            }
            if (!isEmptyRelationships(value)) {
                throw new DataAccessException("Cannot write [" + entity.getName() + "] with its relationships [" + property.getName() + "] not empty, they would be lost: "
                    + "write the relationships to their edge table with a JDBC repository and leave the collection empty");
            }
        }
    }

    private static boolean isEmptyRelationships(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (value instanceof Iterable<?> iterable) {
            return !iterable.iterator().hasNext();
        }
        if (value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value) == 0;
        }
        return false;
    }

    @Override
    public String buildWriteNodeQuery(PersistentEntity entity, List<String> propertyNames, boolean idPresent) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    @Override
    public String buildDeleteByIdQuery(PersistentEntity entity) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    @Override
    public String buildDeleteAllQuery(PersistentEntity entity) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    @Override
    public String buildLinkQuery(Association association, PersistentEntity owner, PersistentEntity target,
                                 List<String> propertyNames, @Nullable PersistentProperty relationshipIdentity) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    @Override
    public String buildCreateLinkQuery(Association association, PersistentEntity owner, PersistentEntity target,
                                       List<String> propertyNames, PersistentProperty relationshipIdentity) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    @Override
    public String buildUpdateLinkQuery(Association association, PersistentEntity owner, PersistentEntity target,
                                       List<String> propertyNames, PersistentProperty relationshipIdentity) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    @Override
    public String buildUnlinkOthersQuery(Association association, PersistentEntity owner, PersistentEntity target) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    @Override
    public String buildUnlinkOtherRelationshipsQuery(Association association, PersistentEntity owner, PersistentEntity target,
                                                     @Nullable PersistentProperty relationshipIdentity) {
        throw new UnsupportedOperationException(READ_ONLY);
    }

    @Override
    public String rootIdentityExpression(PersistentProperty identity) {
        return property(ROOT, identity);
    }

    @Override
    public String buildLimitAndOffset(long limit, long offset) {
        StringBuilder sb = new StringBuilder();
        if (sqlPgq()) {
            if (offset > 0) {
                sb.append(" OFFSET ").append(offset).append(" ROWS");
            }
            if (limit > 0) {
                sb.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
            }
        } else {
            if (offset > 0) {
                sb.append(" OFFSET ").append(offset);
            }
            if (limit > 0) {
                sb.append(" LIMIT ").append(limit);
            }
        }
        return sb.toString();
    }

    @Override
    public String buildFindByIdQuery(PersistentEntity entity) {
        Query query = new Query(entity);
        query.where.append(property(ROOT, identity(entity))).append(" = ?");
        query.entityColumns(ROOT, entity);
        return query.render(false);
    }

    @Override
    public String buildFindAllQuery(PersistentEntity entity) {
        Query query = new Query(entity);
        query.entityColumns(ROOT, entity);
        return query.render(false);
    }

    @Override
    public String buildCountAllQuery(PersistentEntity entity) {
        Query query = new Query(entity);
        query.select.add(COUNT_AS + alias(RESULT));
        query.ref(property(ROOT, identity(entity)));
        return query.render(false);
    }

    @Override
    public QueryResult buildSelect(AnnotationMetadata annotationMetadata, SelectQueryDefinition definition) {
        PersistentEntity entity = definition.persistentEntity();
        for (JoinPath joinPath : definition.getJoinPaths()) {
            if (joinPath.getJoinType().isFetch()) {
                throw new UnsupportedOperationException("Fetching relationships with @Join is not supported for Oracle property graphs: " + joinPath.getPath());
            }
        }
        Query query = new Query(entity);
        appendPredicate(query, definition.predicate());
        for (JoinPath joinPath : definition.getJoinPaths()) {
            if (joinPath.getJoinType() == Join.Type.INNER) {
                // An inner join keeps only the nodes that have the relationship, and needs a pattern if no criterion added one
                query.resolveVariable(List.of(joinPath.getAssociationPath()));
            }
        }
        if (definition.root() instanceof PersistentEntityRoot<?> root) {
            requireDeclaredInnerJoins(query, root.getPersistentJoins());
        }
        Selection<?> selection = definition.selection();
        SelectionRenderer selectionRenderer = new SelectionRenderer(query);
        if (selection instanceof ISelection<?> iSelection) {
            iSelection.visitSelection(selectionRenderer);
        } else {
            selectionRenderer.entity = true;
        }
        if (selectionRenderer.entity) {
            query.entityColumns(ROOT, entity);
        }
        appendOrderBy(query, definition.order());
        boolean distinct = definition.isDistinct() || (selectionRenderer.entity && !query.patterns.isEmpty());
        String text = query.render(distinct);
        if (definition.offset() > 0 || definition.limit() > 0) {
            text += buildLimitAndOffset(definition.limit(), Math.max(definition.offset(), 0));
        } else if (selectionRenderer.literal) {
            text += buildLimitAndOffset(1, 0);
        }
        return QueryResult.of(text, List.of(text), query.bindings, Map.of());
    }

    /**
     * The join paths report an inner join outside the selection as a default join, so counts and scalar projections
     * check the declared joins, which keeps them in line with the query for the entity.
     */
    private static void requireDeclaredInnerJoins(Query query, Collection<? extends PersistentAssociationPath<?, ?>> joins) {
        for (PersistentAssociationPath<?, ?> join : joins) {
            if (join.getAssociationJoinType() == Join.Type.INNER) {
                query.resolveVariable(join.asPath());
            }
            requireDeclaredInnerJoins(query, join.getPersistentJoins());
        }
    }

    private void appendPredicate(Query query, @Nullable Predicate predicate) {
        if (predicate == null) {
            return;
        }
        if (!(predicate instanceof IPredicate iPredicate)) {
            throw new IllegalStateException("Unsupported predicate: " + predicate);
        }
        iPredicate.visitPredicate(new PredicateRenderer(query, query.where));
    }

    private void appendOrderBy(Query query, @Nullable List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        StringBuilder orderBy = new StringBuilder();
        for (Order order : orders) {
            if (!orderBy.isEmpty()) {
                orderBy.append(", ");
            }
            StringBuilder expression = new StringBuilder();
            new ExpressionRenderer(query, expression) {
                @Override
                public void visit(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
                    requireNodeSort(persistentPropertyPath.getPropertyPath());
                    super.visit(persistentPropertyPath);
                }
            }.appendExpression(order.getExpression());
            String ref = query.ref(expression.toString());
            boolean ignoreCase = order instanceof DefaultOrder<?> defaultOrder && defaultOrder.isIgnoreCase();
            orderBy.append(ignoreCase ? LOWER + ref + ")" : ref).append(order.isAscending() ? " ASC" : " DESC");
        }
        query.orderBy = orderBy.toString();
    }

    /**
     * Every pattern of a query is required, as neither dialect has {@code OPTIONAL MATCH}, so sorting on a relationship
     * would drop the nodes without it.
     */
    private static void requireNodeSort(io.micronaut.data.model.PersistentPropertyPath path) {
        boolean association = path.getProperty() instanceof Association target && !target.isEmbedded();
        if (association || !path.getAssociations().isEmpty()) {
            throw new UnsupportedOperationException("Sorting on a relationship is not supported for Oracle property graphs, which have no OPTIONAL MATCH, so nodes without the relationship would be dropped: " + path.getPath());
        }
    }

    @Override
    public QueryResult buildPathQuery(PathQueryDefinition definition) {
        PersistentEntity entity = definition.entity();
        String types = edgeTypes(definition.relationships());
        if (definition.kind() == PathQueryDefinition.Kind.RELATIONSHIPS) {
            return buildRelationshipsQuery(definition, entity, types);
        }
        requireMaxDepth(definition);
        if (!sqlPgq()) {
            requireSupportedPgqlPath(definition);
        }
        // A @PathLimit argument wins over @PathLimit(n) on the method, as it does for Cypher: the runtime renders it
        // in place of the token, capped by micronaut.data.graph.max-path-depth
        String quantifier = "{1," + (definition.maxDepthBound() ? PathQueryDefinition.PATH_LIMIT_TOKEN : String.valueOf(definition.maxDepth())) + "}";
        Query query = new Query(entity);
        boolean elements = definition.result() == PathQueryDefinition.Result.ELEMENTS;
        if (elements && sqlPgq() && definition.kind() != PathQueryDefinition.Kind.ALL) {
            throw new IllegalArgumentException("Shortest paths are not supported by the SQL/PGQ dialect, which has no shortest path search: return every path up to the depth with findAllPaths");
        }
        // SQL/PGQ iterates the steps with variables of their own, so the edge of the pattern takes another name
        String edgeVariable = sqlPgq() ? PATH_EDGE : elements ? EDGE : "";
        String pattern = directed(node(START, entity), edge(edgeVariable, types, "") + quantifier, node(END, entity), definition.direction());
        query.pattern = sqlPgq() ? pattern : pathPrefix(definition.kind(), elements) + pattern;
        if (elements) {
            appendStepColumns(query, definition, entity);
        } else {
            String result = definition.result() == PathQueryDefinition.Result.COUNT ? COUNT_AS : "1 AS ";
            query.select.add(result + alias(RESULT));
            query.ref(property(START, identity(entity)));
        }
        endpoints(query, definition, entity);
        if (definition.start() != null && definition.end() != null && definition.kind() != PathQueryDefinition.Kind.ALL) {
            // A shortest path from a node to itself is the empty path, so the shortest kinds exclude it as the Cypher
            // builder does with s <> e: existsPath(a, a) is false, while the ALL kind of countPaths(a, a) counts the
            // cycles through a (the quantifier starts at one step, so no path of length zero is counted)
            PersistentProperty identity = identity(entity);
            query.and(property(START, identity) + " <> " + property(END, identity));
        }
        if (sqlPgq()) {
            // SQL/PGQ matches walks, which may take an edge more than once, while a Cypher path takes each relationship
            // once, so the match keeps the walks whose edges are distinct
            String edgeId = "JSON_SERIALIZE(EDGE_ID(" + PATH_EDGE + "))";
            query.and("COUNT(DISTINCT " + edgeId + ") = COUNT(" + edgeId + ")");
        }
        String text = query.render(false);
        if (definition.result() == PathQueryDefinition.Result.EXISTS) {
            text += buildLimitAndOffset(1, 0);
        }
        return QueryResult.of(text, List.of(text), query.bindings, Map.of());
    }

    private static String pathPrefix(PathQueryDefinition.Kind kind, boolean elements) {
        return switch (kind) {
            case SHORTEST -> elements ? "ANY SHORTEST " : "ANY ";
            case ALL_SHORTEST -> "ALL SHORTEST ";
            default -> "ALL ";
        };
    }

    private void appendStepColumns(Query query, PathQueryDefinition definition, PersistentEntity entity) {
        query.rowsPer = "ONE ROW PER STEP (" + STEP_START + ", " + EDGE + ", " + STEP_END + ")";
        // SQL/PGQ numbers the match without a variable
        query.column(sqlPgq() ? "MATCHNUM()" : "MATCHNUM(" + STEP_START + ")", PATH_MATCH_COLUMN);
        query.column("ELEMENT_NUMBER(" + EDGE + ")", PATH_STEP_COLUMN);
        query.entityColumns(STEP_START, entity);
        query.entityColumns(STEP_END, entity);
        Set<String> types = new LinkedHashSet<>();
        for (Association relationship : definition.relationships()) {
            types.add(GraphEntities.relationshipType(relationship));
        }
        if (types.size() == 1) {
            query.column(literal(types.iterator().next()), EDGE + SEPARATOR + TYPE_COLUMN);
        } else if (!sqlPgq()) {
            // A path over several relationship types, or over any type, reads the type of each step from the label
            // of its edge, which the graph definition gives each edge table
            query.column("LABEL(" + EDGE + ")", EDGE + SEPARATOR + TYPE_COLUMN);
        } else if (types.isEmpty()) {
            throw new IllegalArgumentException("Paths over any relationship type are not supported by the SQL/PGQ dialect, which cannot read the label of an edge: name the relationships of the path");
        } else {
            // SQL/PGQ has no function returning the label, so each step tests the labels of the path
            StringBuilder type = new StringBuilder("CASE");
            for (String name : types) {
                type.append(" WHEN ").append(EDGE).append(" IS LABELED ").append(identifier(name)).append(" THEN ").append(literal(name));
            }
            query.column(type.append(" END").toString(), EDGE + SEPARATOR + TYPE_COLUMN);
        }
        // SQL/PGQ orders the rows of the graph table by their columns
        query.orderBy = sqlPgq()
            ? alias(PATH_MATCH_COLUMN) + ", " + alias(PATH_STEP_COLUMN)
            : "MATCHNUM(" + STEP_START + "), ELEMENT_NUMBER(" + EDGE + ")";
    }

    private QueryResult buildRelationshipsQuery(PathQueryDefinition definition, PersistentEntity entity, String types) {
        if (definition.relationships().size() != 1) {
            throw new IllegalArgumentException("Relationship finders on Oracle property graphs need exactly one relationship type, for example findRelationshipsByFriends");
        }
        // An undirected relationship is matched in either direction once both of its ends are known
        boolean bothEnds = definition.start() != null && definition.end() != null;
        Direction direction = definition.direction() == Direction.UNDIRECTED && !bothEnds ? Direction.OUTGOING : definition.direction();
        // The rows read the source and destination of the edge from s and t, so an incoming edge binds them the other way
        String startVariable = direction == Direction.INCOMING ? END : START;
        String endVariable = direction == Direction.INCOMING ? START : END;
        Query query = new Query(entity);
        query.pattern = directed(node(startVariable, entity), edge("r", types, ""), node(endVariable, entity), direction);
        endpoints(query, definition, startVariable, endVariable);
        switch (definition.result()) {
            case COUNT -> query.select.add(COUNT_AS + alias(RESULT));
            case EXISTS -> query.select.add("1 AS " + alias(RESULT));
            default -> {
                query.entityColumns(START, entity);
                query.entityColumns(END, entity);
                PersistentEntity associated = definition.relationships().get(0).getAssociatedEntity();
                if (associated != null && GraphEntities.isRelationshipEntity(associated)) {
                    query.edgeColumns("r", associated);
                }
                query.column(literal(GraphEntities.relationshipType(definition.relationships().get(0))), "r" + SEPARATOR + TYPE_COLUMN);
                if (direction == Direction.UNDIRECTED) {
                    // s is the first argument here, which may be either end of the edge
                    query.column("CASE WHEN " + START + " IS SOURCE OF r THEN 1 ELSE 0 END", "r" + SEPARATOR + FORWARD_COLUMN);
                }
            }
        }
        if (query.select.isEmpty() || definition.result() != PathQueryDefinition.Result.ELEMENTS) {
            query.ref(property(START, identity(entity)));
        }
        String text = query.render(false);
        if (definition.result() == PathQueryDefinition.Result.EXISTS) {
            text += buildLimitAndOffset(1, 0);
        }
        return QueryResult.of(text, List.of(text), query.bindings, Map.of());
    }

    /**
     * Oracle searches paths up to a bounded quantifier only: the depth comes from {@code @PathLimit(n)} on the method
     * or from a {@code @PathLimit} argument, which the runtime renders in place of the token.
     */
    private static void requireMaxDepth(PathQueryDefinition definition) {
        if (!definition.maxDepthBound() && definition.maxDepth() <= 0) {
            throw new IllegalArgumentException("Oracle property graph path queries need a maximum depth: annotate the method with @PathLimit(n), or add an int parameter annotated with @PathLimit");
        }
    }

    /**
     * Oracle documents that PGQL on a property graph in the database has no {@code ALL SHORTEST} search, and that a
     * path query with bind variables fails with "Use of bind variables for path queries is not supported".
     */
    private static void requireSupportedPgqlPath(PathQueryDefinition definition) {
        if (definition.kind() == PathQueryDefinition.Kind.ALL_SHORTEST) {
            throw new IllegalArgumentException("ALL SHORTEST is not supported by PGQL on Oracle property graphs");
        }
        if (definition.start() != null || definition.end() != null) {
            throw new IllegalArgumentException("PGQL path queries cannot bind parameters on Oracle property graphs: use @PropertyGraph(language = SQL_PGQ) to check for paths and count them");
        }
    }

    private void endpoints(Query query, PathQueryDefinition definition, PersistentEntity entity) {
        endpoints(query, definition, START, END);
    }

    private void endpoints(Query query, PathQueryDefinition definition, String startVariable, String endVariable) {
        PersistentProperty property = definition.endpointMatchProperty();
        io.micronaut.data.model.PersistentPropertyPath path = io.micronaut.data.model.PersistentPropertyPath.of(List.of(), property);
        if (definition.start() != null) {
            query.and(property(startVariable, property) + " = ?");
            query.push(definition.start(), path);
        }
        if (definition.end() != null) {
            query.and(property(endVariable, property) + " = ?");
            query.push(definition.end(), path);
        }
    }

    private String node(String variable, PersistentEntity entity) {
        String label = identifier(GraphEntities.primaryLabel(entity));
        return sqlPgq() ? "(" + variable + " IS " + label + ")" : "(" + variable + ":" + label + ")";
    }

    private String edge(String variable, String types, String suffix) {
        if (types.isEmpty()) {
            return "[" + variable + "]" + suffix;
        }
        return sqlPgq() ? "[" + variable + (variable.isEmpty() ? "" : " ") + "IS " + types + "]" + suffix : "[" + variable + ":" + types + "]" + suffix;
    }

    private static String edgeTypes(List<Association> associations) {
        List<String> types = new ArrayList<>();
        for (Association association : associations) {
            String type = identifier(GraphEntities.relationshipType(association));
            if (!types.contains(type)) {
                types.add(type);
            }
        }
        return String.join("|", types);
    }

    private static String directed(String from, String edge, String to, Direction direction) {
        int quantifier = edge.indexOf(']') + 1;
        String rel = edge.substring(0, quantifier);
        String suffix = edge.substring(quantifier);
        return switch (direction) {
            case OUTGOING -> from + " -" + rel + "->" + suffix + " " + to;
            case INCOMING -> from + " <-" + rel + "-" + suffix + " " + to;
            case UNDIRECTED -> from + " -" + rel + "-" + suffix + " " + to;
        };
    }

    private static String property(String variable, PersistentProperty property) {
        return variable + "." + identifier(GraphEntities.propertyName(property));
    }

    /**
     * Quotes a label, relationship type or property name for SQL/PGQ and PGQL, which use double quotes
     * where Cypher uses backticks.
     *
     * @param name The name
     * @return The identifier
     */
    private static String identifier(String name) {
        if (GraphEntities.isPlainIdentifier(name)) {
            return name;
        }
        return "\"" + GraphEntities.requireName(name).replace("\"", "\"\"") + "\"";
    }

    /**
     * The name of the property graph, which is spliced into every statement and so must be a plain
     * identifier or a quoted one.
     *
     * @param name The name from the annotation
     * @return The name
     */
    private static String graphName(String name) {
        if (GraphEntities.isPlainIdentifier(name)) {
            return name;
        }
        throw new IllegalArgumentException("Property graph name [" + name + "] is not a plain identifier: a name needs a letter followed by letters, digits or underscores");
    }

    /**
     * The identifier property of an entity, which every Oracle property graph query needs: vertex
     * tables carry their key and the rows of a query are matched through it.
     *
     * @param entity The entity
     * @return The identity
     */
    private static PersistentProperty identity(PersistentEntity entity) {
        if (!entity.hasIdentity()) {
            throw new IllegalArgumentException("Entity [" + entity.getName() + "] has no identifier: an Oracle property graph node needs an @Id property");
        }
        return entity.getIdentity();
    }

    private static String alias(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private static String literal(@Nullable Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean b) {
            return b ? "TRUE" : "FALSE";
        }
        if (value instanceof Enum<?> e) {
            return "'" + e.name().replace("'", "''") + "'";
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    /**
     * A query being built: its patterns, conditions, columns and parameters.
     */
    private final class Query {
        final PersistentEntity entity;
        final Map<String, String[]> patterns = new LinkedHashMap<>();
        final StringBuilder where = new StringBuilder();
        final List<String> select = new ArrayList<>();
        final Map<String, String> columns = new LinkedHashMap<>();
        final List<QueryParameterBinding> bindings = new ArrayList<>();
        @Nullable String pattern;
        @Nullable String rowsPer;
        @Nullable String orderBy;
        int position;

        Query(PersistentEntity entity) {
            this.entity = entity;
        }

        void and(String condition) {
            if (!where.isEmpty()) {
                where.append(AND);
            }
            where.append(condition);
        }

        void push(BindingParameter parameter, io.micronaut.data.model.@Nullable PersistentPropertyPath propertyPath) {
            int index = position++;
            bindings.add(parameter.bind(BindingParameter.BindingContext.create()
                .index(index)
                .name("p" + index)
                .incomingMethodParameterProperty(propertyPath)
                .outgoingQueryParameterProperty(propertyPath)));
        }

        /**
         * Adds a column with an explicit alias.
         */
        void column(String expression, String name) {
            String quoted = alias(name);
            columns.put(quoted, expression);
            if (!sqlPgq()) {
                select.add(expression + " AS " + quoted);
            }
        }

        /**
         * Adds the columns of a node: its properties, identifier and label.
         */
        void entityColumns(String variable, PersistentEntity nodeEntity) {
            PersistentProperty identity = nodeEntity.getIdentity();
            if (identity != null) {
                column(property(variable, identity), variable + SEPARATOR + ID_COLUMN);
                column(property(variable, identity), variable + SEPARATOR + GraphEntities.propertyName(identity));
            }
            column(literal(GraphEntities.primaryLabel(nodeEntity)), variable + SEPARATOR + LABEL_COLUMN);
            for (PersistentProperty property : nodeEntity.getPersistentProperties()) {
                if (property.getAnnotationMetadata().hasAnnotation(DynamicRelationships.class)) {
                    throw new UnsupportedOperationException("Dynamic relationships are not supported for Oracle property graphs: " + nodeEntity.getName());
                }
                if (property.getAnnotationMetadata().hasAnnotation(Labels.class)) {
                    // The labels of a vertex table come from the graph definition
                    throw new IllegalArgumentException("Dynamic labels are not supported by Oracle property graphs, entity ["
                        + nodeEntity.getName() + "] declares [" + property.getName() + "]");
                }
                if (property.getAnnotationMetadata().hasAnnotation(DynamicAttributes.class)) {
                    // Schemaless properties are stored in a JSON column, whose entries become properties of the node
                    column(property(variable, property), variable + SEPARATOR + DYNAMIC_ATTRIBUTES_COLUMN);
                    continue;
                }
                if (property instanceof Association) {
                    continue;
                }
                column(property(variable, property), variable + SEPARATOR + GraphEntities.propertyName(property));
            }
        }

        /**
         * Adds the columns of a relationship entity: its identifier and properties, which the edge table exposes.
         */
        void edgeColumns(String variable, PersistentEntity relationshipEntity) {
            PersistentProperty identity = relationshipEntity.getIdentity();
            if (identity != null) {
                column(property(variable, identity), variable + SEPARATOR + ID_COLUMN);
            }
            for (PersistentProperty property : relationshipEntity.getPersistentProperties()) {
                if (!(property instanceof Association)) {
                    column(property(variable, property), variable + SEPARATOR + GraphEntities.propertyName(property));
                }
            }
        }

        /**
         * References a graph expression outside the pattern match. SQL/PGQ exposes it as a column of the graph table.
         */
        String ref(String expression) {
            if (!sqlPgq()) {
                return expression;
            }
            for (Map.Entry<String, String> entry : columns.entrySet()) {
                if (entry.getValue().equals(expression)) {
                    return entry.getKey();
                }
            }
            String name = alias("c" + columns.size());
            columns.put(name, expression);
            return name;
        }

        String render(boolean distinct) {
            StringBuilder sb = new StringBuilder("SELECT ");
            if (distinct) {
                sb.append("DISTINCT ");
            }
            if (sqlPgq()) {
                renderGraphTable(sb);
            } else {
                renderPgql(sb);
            }
            if (orderBy != null) {
                sb.append(" ORDER BY ").append(orderBy);
            }
            return sb.toString();
        }

        private void renderGraphTable(StringBuilder sb) {
            String match = pattern;
            if (match == null) {
                List<String> parts = new ArrayList<>();
                parts.add(node(ROOT, entity));
                for (String[] value : patterns.values()) {
                    parts.add(value[0]);
                }
                match = String.join(", ", parts);
            }
            sb.append(select.isEmpty() ? "*" : String.join(", ", select));
            sb.append(" FROM GRAPH_TABLE (").append(graph).append(" MATCH ").append(match);
            appendRowsAndWhere(sb);
            sb.append(" COLUMNS (");
            Iterator<Map.Entry<String, String>> iterator = columns.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> entry = iterator.next();
                sb.append(entry.getValue()).append(" AS ").append(entry.getKey());
                if (iterator.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("))");
        }

        private void renderPgql(StringBuilder sb) {
            sb.append(String.join(", ", select));
            sb.append(" FROM ");
            if (pattern != null) {
                sb.append("MATCH ").append(pattern).append(" ON ").append(graph);
            } else {
                sb.append("MATCH ").append(node(ROOT, entity)).append(" ON ").append(graph);
                for (String[] value : patterns.values()) {
                    sb.append(", MATCH ").append(value[0]).append(" ON ").append(graph);
                }
            }
            appendRowsAndWhere(sb);
        }

        private void appendRowsAndWhere(StringBuilder sb) {
            // PGQL puts ONE ROW PER STEP before the WHERE clause, SQL/PGQ after it
            if (rowsPer != null && !sqlPgq()) {
                sb.append(' ').append(rowsPer);
            }
            if (!where.isEmpty()) {
                sb.append(" WHERE ").append(where);
            }
            if (rowsPer != null && sqlPgq()) {
                sb.append(' ').append(rowsPer);
            }
        }

        /**
         * Resolves a property path to a graph expression, adding relationship patterns for associations.
         */
        String resolve(io.micronaut.data.model.PersistentPropertyPath propertyPath) {
            String variable = resolveVariable(propertyPath.getAssociations());
            PersistentProperty property = propertyPath.getProperty();
            if (property instanceof Association association && !association.isEmbedded()) {
                // A criterion on an association itself compares the identifier of the associated entity, as the Cypher
                // builder does. For a relationship entity that is the edge, whose target node is compared through the
                // @TargetNode association, which is also what an entity without an identifier compares.
                PersistentEntity associated = association.getAssociatedEntity();
                List<Association> associations = new ArrayList<>(propertyPath.getAssociations());
                associations.add(association);
                if (associated.hasIdentity()) {
                    return resolve(io.micronaut.data.model.PersistentPropertyPath.of(associations, associated.getIdentity()));
                }
                Association targetNode = GraphEntities.findTargetNode(associated);
                if (targetNode != null) {
                    associations.add(targetNode);
                }
                return resolve(io.micronaut.data.model.PersistentPropertyPath.of(associations, identity(GraphEntities.targetNodeEntity(association))));
            }
            return property(variable, property);
        }

        /**
         * Adds the relationship patterns of a path of associations.
         *
         * @return The variable at the end of the path
         */
        String resolveVariable(List<Association> associations) {
            String variable = ROOT;
            String relationshipEntityNode = null;
            StringBuilder path = new StringBuilder();
            for (Association association : associations) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(association.getName());
                if (relationshipEntityNode != null && association.getAnnotationMetadata().hasStereotype(TargetNode.class)) {
                    variable = relationshipEntityNode;
                    relationshipEntityNode = null;
                    continue;
                }
                if (!GraphEntities.isRelationship(association)) {
                    throw new IllegalArgumentException("Association [" + association.getName() + "] is not a @Relationship and cannot be queried");
                }
                String from = variable;
                String[] match = patterns.computeIfAbsent(path.toString(), key -> newPattern(association, from));
                if (!match[2].isEmpty()) {
                    variable = match[2];
                    relationshipEntityNode = match[1];
                } else {
                    variable = match[1];
                }
            }
            return variable;
        }

        private String[] newPattern(Association association, String from) {
            int index = patterns.size();
            String nodeVariable = "a" + index;
            PersistentEntity target = GraphEntities.targetNodeEntity(association);
            boolean relationshipEntity = GraphEntities.isRelationshipEntity(association.getAssociatedEntity());
            String relVariable = relationshipEntity ? "ar" + index : "";
            String text = directed("(" + from + ")",
                edge(relVariable, identifier(GraphEntities.relationshipType(association)), ""),
                node(nodeVariable, target), GraphEntities.direction(association));
            return new String[] {text, nodeVariable, relVariable};
        }
    }

    /**
     * Renders expressions in the graph pattern scope.
     */
    private class ExpressionRenderer implements io.micronaut.data.model.jpa.criteria.impl.ExpressionVisitor {
        final Query query;
        final StringBuilder out;
        int depth;
        private io.micronaut.data.model.@Nullable PersistentPropertyPath bound;

        ExpressionRenderer(Query query, StringBuilder out) {
            this.query = query;
            this.out = out;
        }

        void appendExpression(Expression<?> expression) {
            appendExpression(expression, null);
        }

        void appendExpression(Expression<?> expression, io.micronaut.data.model.@Nullable PersistentPropertyPath boundProperty) {
            io.micronaut.data.model.PersistentPropertyPath previous = bound;
            bound = boundProperty;
            depth++;
            try {
                if (expression instanceof BindingParameter parameter) {
                    out.append('?');
                    query.push(parameter, boundProperty);
                } else if (expression instanceof IExpression<?> iExpression) {
                    iExpression.visitExpression(this);
                } else {
                    throw new IllegalStateException("Unsupported expression: " + expression);
                }
            } finally {
                depth--;
                bound = previous;
            }
        }

        @Override
        public void visit(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            out.append(query.resolve(persistentPropertyPath.getPropertyPath()));
        }

        @Override
        public void visit(PersistentEntityRoot<?> entityRoot) {
            out.append(property(ROOT, identity(query.entity)));
        }

        @Override
        public void visit(PersistentEntitySubquery<?> subquery) {
            throw new UnsupportedOperationException("Subqueries are not supported for Oracle property graphs");
        }

        @Override
        public void visit(LiteralExpression<?> literalExpression) {
            out.append(literal(literalExpression.getValue()));
        }

        @Override
        public void visit(UnaryExpression<?> unaryExpression) {
            String function = switch (unaryExpression.getType()) {
                case AVG -> "AVG";
                case SUM -> "SUM";
                case MAX -> "MAX";
                case MIN -> "MIN";
                case UPPER -> "UPPER";
                case LOWER -> "LOWER";
                case LENGTH -> "LENGTH";
                case COUNT -> "COUNT";
                case COUNT_DISTINCT -> "COUNT";
            };
            out.append(function).append('(');
            if (unaryExpression.getType() == io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpressionType.COUNT_DISTINCT) {
                out.append("DISTINCT ");
            }
            appendExpression(unaryExpression.getExpression(), bound);
            out.append(')');
        }

        @Override
        public void visit(BinaryExpression<?> binaryExpression) {
            out.append('(');
            appendExpression(binaryExpression.getLeft(), bound);
            out.append(switch (binaryExpression.getType()) {
                case SUM -> " + ";
                case CONCAT -> " || ";
                case PROD -> " * ";
                case QUOT -> " / ";
                default -> throw new UnsupportedOperationException("Unsupported binary expression: " + binaryExpression.getType());
            });
            appendExpression(binaryExpression.getRight(), bound);
            out.append(')');
        }

        @Override
        public void visit(IdExpression<?, ?> idExpression) {
            out.append(property(ROOT, identity(query.entity)));
        }

        @Override
        public void visit(FunctionExpression<?> functionExpression) {
            out.append(functionExpression.getName()).append('(');
            Iterator<Expression<?>> iterator = functionExpression.getExpressions().iterator();
            while (iterator.hasNext()) {
                appendExpression(iterator.next(), bound);
                if (iterator.hasNext()) {
                    out.append(", ");
                }
            }
            out.append(')');
        }

        @Override
        public void visit(IParameterExpression<?> parameterExpression) {
            out.append('?');
            query.push(parameterExpression, bound);
        }

        @Override
        public void visit(SubqueryExpression<?> subqueryExpression) {
            throw new UnsupportedOperationException("Subqueries are not supported for Oracle property graphs");
        }

        @Override
        public void visit(CastExpression<?> castExpression) {
            throw new UnsupportedOperationException("CAST is not supported for Oracle property graphs");
        }

        @Override
        public void visit(CurrentTemporalExpression<?> currentTemporalExpression) {
            out.append(switch (currentTemporalExpression.getType()) {
                case DATE -> "CURRENT_DATE";
                case TIME, TIMESTAMP -> "CURRENT_TIMESTAMP";
            });
        }

        io.micronaut.data.model.@Nullable PersistentPropertyPath propertyPathOf(@Nullable Expression<?> expression) {
            if (expression instanceof io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> path) {
                return path.getPropertyPath();
            }
            if (expression instanceof UnaryExpression<?> unary) {
                return propertyPathOf(unary.getExpression());
            }
            return null;
        }
    }

    /**
     * Renders predicates.
     */
    private final class PredicateRenderer extends ExpressionRenderer implements AdvancedPredicateVisitor<io.micronaut.data.model.PersistentPropertyPath> {
        private boolean negated;

        PredicateRenderer(Query query, StringBuilder out) {
            super(query, out);
        }

        @Override
        public io.micronaut.data.model.PersistentPropertyPath getRequiredProperty(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            return persistentPropertyPath.getPropertyPath();
        }

        private void visitBoolean(IExpression<Boolean> expression) {
            if (expression instanceof IPredicate predicate) {
                predicate.visitPredicate(this);
            } else {
                visitIsTrue(expression);
            }
        }

        private void junction(Collection<? extends IExpression<Boolean>> predicates, String operator, String empty) {
            if (predicates.isEmpty()) {
                out.append(empty);
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
            junction(conjunction.getPredicates(), AND, "1 = 1");
        }

        @Override
        public void visit(DisjunctionPredicate disjunction) {
            junction(disjunction.getPredicates(), " OR ", "1 = 0");
        }

        @Override
        public void visit(NegatedPredicate negate) {
            out.append("NOT (");
            negated = !negated;
            visitBoolean(negate.getNegated());
            negated = !negated;
            out.append(')');
        }

        @Override
        public void visit(InPredicate<?> inPredicate) {
            visitIn(inPredicate.getExpression(), inPredicate.getValues(), false);
        }

        @Override
        public void visitIn(Expression<?> expression, Collection<?> values, boolean negated) {
            io.micronaut.data.model.PersistentPropertyPath path = propertyPathOf(expression);
            appendExpression(expression);
            out.append(negated ? " NOT IN (" : " IN (");
            Iterator<?> iterator = values.iterator();
            while (iterator.hasNext()) {
                Object value = iterator.next();
                if (value instanceof Expression<?> e) {
                    appendExpression(e, path);
                } else {
                    out.append(literal(value));
                }
                if (iterator.hasNext()) {
                    out.append(", ");
                }
            }
            out.append(')');
        }

        private void comparison(Expression<?> left, String operator, Expression<?> right, boolean ignoreCase) {
            io.micronaut.data.model.PersistentPropertyPath path = propertyPathOf(left);
            if (path == null) {
                path = propertyPathOf(right);
            }
            if (ignoreCase) {
                out.append(LOWER);
                appendExpression(left, path);
                out.append(')').append(operator).append(LOWER);
                appendExpression(right, path);
                out.append(')');
            } else {
                appendExpression(left, path);
                out.append(operator);
                appendExpression(right, path);
            }
        }

        private void like(Expression<?> left, Expression<?> right, boolean ignoreCase, String prefix, String suffix) {
            if (!sqlPgq()) {
                throw new UnsupportedOperationException("PGQL has no LIKE: use exact matches, a regular expression with Regex, or @PropertyGraph(language = SQL_PGQ)");
            }
            io.micronaut.data.model.PersistentPropertyPath path = propertyPathOf(left);
            if (ignoreCase) {
                out.append(LOWER);
            }
            appendExpression(left, path);
            if (ignoreCase) {
                out.append(')');
            }
            out.append(" LIKE ").append(prefix);
            if (ignoreCase) {
                out.append(LOWER);
            }
            appendExpression(right, path);
            if (ignoreCase) {
                out.append(')');
            }
            out.append(suffix);
        }

        @Override
        public void visitIdEquals(Expression<?> expression) {
            PersistentProperty identity = identity(query.entity);
            out.append(property(ROOT, identity)).append(" = ");
            appendExpression(expression, io.micronaut.data.model.PersistentPropertyPath.of(List.of(), identity));
        }

        @Override
        public void visitEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            comparison(leftExpression, " = ", rightExpression, ignoreCase);
        }

        @Override
        public void visitNotEquals(Expression<?> leftExpression, Expression<?> rightExpression, boolean ignoreCase) {
            comparison(leftExpression, " <> ", rightExpression, ignoreCase);
        }

        @Override
        public void visitGreaterThan(Expression<?> leftExpression, Expression<?> rightExpression) {
            comparison(leftExpression, " > ", rightExpression, false);
        }

        @Override
        public void visitGreaterThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression) {
            comparison(leftExpression, " >= ", rightExpression, false);
        }

        @Override
        public void visitLessThan(Expression<?> leftExpression, Expression<?> rightExpression) {
            comparison(leftExpression, " < ", rightExpression, false);
        }

        @Override
        public void visitLessThanOrEquals(Expression<?> leftExpression, Expression<?> rightExpression) {
            comparison(leftExpression, " <= ", rightExpression, false);
        }

        @Override
        public void visitStartsWith(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase) {
            like(leftExpression, expression, ignoreCase, "", " || '%'");
        }

        @Override
        public void visitEndsWith(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase) {
            like(leftExpression, expression, ignoreCase, "'%' || ", "");
        }

        @Override
        public void visitContains(Expression<?> leftExpression, Expression<?> expression, boolean ignoreCase) {
            like(leftExpression, expression, ignoreCase, "'%' || ", " || '%'");
        }

        @Override
        public void visitRegexp(Expression<?> leftExpression, Expression<?> expression) {
            io.micronaut.data.model.PersistentPropertyPath path = propertyPathOf(leftExpression);
            out.append(sqlPgq() ? "REGEXP_LIKE(" : "JAVA_REGEXP_LIKE(");
            appendExpression(leftExpression, path);
            out.append(", ");
            appendExpression(expression, path);
            out.append(')');
        }

        @Override
        public void visitInBetween(Expression<?> value, Expression<?> from, Expression<?> to, boolean negated) {
            if (negated) {
                out.append("NOT ");
            }
            out.append('(');
            comparison(value, " >= ", from, false);
            out.append(AND);
            comparison(value, " <= ", to, false);
            out.append(')');
        }

        /**
         * A relationship is matched by a required pattern, so its identifier is never null and a test for its absence
         * could never match. An existence test is not possible either, as GRAPH_TABLE does not allow subqueries.
         */
        private void rejectAbsentRelationship(Expression<?> expression, boolean absent) {
            io.micronaut.data.model.PersistentPropertyPath path = propertyPathOf(expression);
            if (!absent || path == null) {
                return;
            }
            PersistentProperty property = path.getProperty();
            boolean association = property instanceof Association target && !target.isEmbedded();
            PersistentProperty ownerIdentity = property.getOwner().getIdentity();
            boolean identifier = !path.getAssociations().isEmpty() && ownerIdentity != null && ownerIdentity.getName().equals(property.getName());
            if (association || identifier) {
                throw new UnsupportedOperationException("Testing that a relationship is absent is not supported for Oracle property graphs, as a subquery is not allowed in GRAPH_TABLE: write a query with NOT EXISTS outside GRAPH_TABLE using @Query instead: " + path.getPath());
            }
        }

        @Override
        public void visitIsNull(Expression<?> expression) {
            rejectAbsentRelationship(expression, !negated);
            appendExpression(expression);
            out.append(" IS NULL");
        }

        @Override
        public void visitIsNotNull(Expression<?> expression) {
            rejectAbsentRelationship(expression, negated);
            appendExpression(expression);
            out.append(" IS NOT NULL");
        }

        @Override
        public void visitIsTrue(Expression<?> expression) {
            appendExpression(expression);
            out.append(" = TRUE");
        }

        @Override
        public void visitIsFalse(Expression<?> expression) {
            appendExpression(expression);
            out.append(" = FALSE");
        }

        @Override
        public void visitIsEmpty(Expression<?> expression) {
            rejectAbsentRelationship(expression, !negated);
            appendExpression(expression);
            out.append(" IS NULL");
        }

        @Override
        public void visitIsNotEmpty(Expression<?> expression) {
            rejectAbsentRelationship(expression, negated);
            appendExpression(expression);
            out.append(" IS NOT NULL");
        }

        @Override
        public void visit(LikePredicate likePredicate) {
            if (!sqlPgq()) {
                throw new UnsupportedOperationException("PGQL has no LIKE: use exact matches, a regular expression with Regex, or @PropertyGraph(language = SQL_PGQ)");
            }
            if (likePredicate.isNegated()) {
                out.append("NOT ");
            }
            appendExpression(likePredicate.getExpression());
            out.append(" LIKE ");
            appendExpression(likePredicate.getPattern(), propertyPathOf(likePredicate.getExpression()));
        }

        @Override
        public void visit(ExistsSubqueryPredicate existsSubqueryPredicate) {
            throw new UnsupportedOperationException("Subqueries are not supported for Oracle property graphs");
        }
    }

    /**
     * Renders the selected columns.
     */
    private final class SelectionRenderer implements SelectionVisitor {
        final Query query;
        boolean entity;
        boolean literal;
        private @Nullable String alias;

        SelectionRenderer(Query query) {
            this.query = query;
        }

        private void item(Expression<?> expression, String defaultAlias, @Nullable String aggregate) {
            StringBuilder graphExpression = new StringBuilder();
            new ExpressionRenderer(query, graphExpression).appendExpression(expression);
            String ref = query.ref(graphExpression.toString());
            String name = alias(alias != null ? alias : defaultAlias);
            query.select.add((aggregate == null ? ref : aggregate.replace("{}", ref)) + " AS " + name);
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
            for (Selection<?> selection : compoundSelection.getCompoundSelectionItems()) {
                ((ISelection<?>) selection).visitSelection(this);
            }
        }

        @Override
        public void visit(PersistentEntityRoot<?> entityRoot) {
            entity = true;
        }

        @Override
        public void visit(io.micronaut.data.model.jpa.criteria.PersistentPropertyPath<?> persistentPropertyPath) {
            item(persistentPropertyPath, persistentPropertyPath.getProperty().getName(), null);
        }

        @Override
        public void visit(PersistentEntitySubquery<?> subquery) {
            throw new UnsupportedOperationException("Subqueries are not supported for Oracle property graphs");
        }

        @Override
        public void visit(LiteralExpression<?> literalExpression) {
            literal = true;
            query.ref(property(ROOT, identity(query.entity)));
            query.select.add(literal(literalExpression.getValue()) + " AS " + alias(alias != null ? alias : RESULT));
        }

        @Override
        public void visit(UnaryExpression<?> unaryExpression) {
            Expression<?> expression = unaryExpression.getExpression();
            if (expression instanceof PersistentEntityRoot<?>) {
                String id = query.ref(property(ROOT, identity(query.entity)));
                boolean distinct = unaryExpression.getType() == io.micronaut.data.model.jpa.criteria.impl.expression.UnaryExpressionType.COUNT_DISTINCT
                    || !query.patterns.isEmpty();
                query.select.add((distinct ? "COUNT(DISTINCT " + id + ")" : "COUNT(*)") + " AS " + alias(alias != null ? alias : RESULT));
                return;
            }
            String aggregate = switch (unaryExpression.getType()) {
                case AVG -> "AVG({})";
                case SUM -> "SUM({})";
                case MAX -> "MAX({})";
                case MIN -> "MIN({})";
                case COUNT -> "COUNT({})";
                case COUNT_DISTINCT -> "COUNT(DISTINCT {})";
                case UPPER -> "UPPER({})";
                case LOWER -> "LOWER({})";
                case LENGTH -> "LENGTH({})";
            };
            item(expression, RESULT, aggregate);
        }

        @Override
        public void visit(BinaryExpression<?> binaryExpression) {
            item(binaryExpression, RESULT, null);
        }

        @Override
        public void visit(IdExpression<?, ?> idExpression) {
            String ref = query.ref(property(ROOT, identity(query.entity)));
            query.select.add(ref + " AS " + alias(alias != null ? alias : identity(query.entity).getName()));
        }

        @Override
        public void visit(FunctionExpression<?> functionExpression) {
            item(functionExpression, RESULT, null);
        }

        @Override
        public void visit(CastExpression<?> castExpression) {
            throw new UnsupportedOperationException("CAST is not supported for Oracle property graphs");
        }

        @Override
        public void visit(CurrentTemporalExpression<?> currentTemporalExpression) {
            throw new UnsupportedOperationException("Selecting the current time is not supported for Oracle property graphs");
        }
    }
    private record VertexInsert(PersistentEntity persistentEntity, boolean returning) implements InsertQueryDefinition {
    }

    private record VertexUpdate(UpdateQueryDefinition definition,
                                PersistentEntity persistentEntity,
                                Map<String, Object> propertiesToUpdate,
                                boolean isGeneratedEntityUpdate) implements GeneratedEntityUpdateQueryDefinition {

        @Override
        public @Nullable Selection<?> returningSelection() {
            return definition.returningSelection();
        }

        @Override
        public @Nullable Predicate predicate() {
            return definition.predicate();
        }

        @Override
        public Collection<JoinPath> getJoinPaths() {
            return definition.getJoinPaths();
        }

        @Override
        public Optional<JoinPath> getJoinPath(String path) {
            return definition.getJoinPath(path);
        }

        @Override
        public int limit() {
            return definition.limit();
        }

        @Override
        public int offset() {
            return definition.offset();
        }
    }

    private record VertexDelete(DeleteQueryDefinition definition, PersistentEntity persistentEntity) implements DeleteQueryDefinition {

        @Override
        public @Nullable Selection<?> returningSelection() {
            return definition.returningSelection();
        }

        @Override
        public @Nullable Predicate predicate() {
            return definition.predicate();
        }

        @Override
        public Collection<JoinPath> getJoinPaths() {
            return definition.getJoinPaths();
        }

        @Override
        public Optional<JoinPath> getJoinPath(String path) {
            return definition.getJoinPath(path);
        }

        @Override
        public int limit() {
            return definition.limit();
        }

        @Override
        public int offset() {
            return definition.offset();
        }
    }
}
