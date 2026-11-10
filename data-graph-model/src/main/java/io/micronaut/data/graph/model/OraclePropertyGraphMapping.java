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
package io.micronaut.data.graph.model;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.sql.SqlMembers;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The mapping of graph entities to the edge tables of an Oracle property graph, and the conflicts that make a mapping
 * impossible. It works on any {@link PersistentEntity} model, so the same rules run when the schema is generated at
 * startup and when an Oracle graph repository is compiled.
 *
 * <ul>
 *     <li>A relationship entity is the edge table of one node only.</li>
 *     <li>Two relationships that map to the same edge table must have the same relationship type and ends, since nothing
 *     in a row says which of the two it belongs to.</li>
 *     <li>The two sides of one relationship, declared on both nodes with opposite directions, share one edge table, so
 *     only one of them may declare a relationship entity.</li>
 * </ul>
 *
 * <p>The compile-time check only sees the nodes reachable from the entity of one repository. Conflicts between nodes
 * that only meet in the graph built from several repositories are reported when the schema is generated.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class OraclePropertyGraphMapping {

    private OraclePropertyGraphMapping() {
    }

    /**
     * Checks the mapping of the nodes reachable from a root node through their relationships and relationship
     * entities. Relationships whose mapping is incomplete, such as a relationship entity without a target node, are
     * left to the checks that report them.
     *
     * @param root The root node
     * @throws IllegalStateException If the mapping has a conflict
     */
    public static void checkReachable(PersistentEntity root) {
        List<EdgeTable> edges = new ArrayList<>();
        Map<String, PersistentEntity> owners = new LinkedHashMap<>();
        for (PersistentEntity node : reachableNodes(root)) {
            for (PersistentProperty property : node.getPersistentProperties()) {
                if (property instanceof Association association && GraphEntities.isRelationship(association)) {
                    EdgeTable edge = reachableEdge(owners, node, association);
                    if (edge != null) {
                        edges.add(edge);
                    }
                }
            }
        }
        mergeEdges(edges, Function.identity());
    }

    /**
     * The edge table of a relationship of a reachable node, or null for a relationship entity without a target node.
     */
    private static @Nullable EdgeTable reachableEdge(Map<String, PersistentEntity> owners, PersistentEntity node, Association association) {
        PersistentEntity associated = association.getAssociatedEntity();
        if (!GraphEntities.isRelationshipEntity(associated)) {
            return edgeTable(node, association, associated,
                qualifiedTableName(schemaName(node), nodeEdgeTableName(node, association)), false);
        }
        checkRelationshipEntityOwner(owners, node, associated);
        Association targetNode = GraphEntities.findTargetNode(associated);
        if (targetNode == null) {
            return null;
        }
        return edgeTable(node, association, targetNode.getAssociatedEntity(),
            qualifiedTableName(schemaName(associated), associated.getPersistedName()), true);
    }

    /**
     * Records the node that owns a relationship entity.
     *
     * @param owners             The owners of the relationship entities seen so far, by entity name
     * @param node               The node declaring a relationship to the relationship entity
     * @param relationshipEntity The relationship entity
     * @throws IllegalStateException If another node uses the relationship entity
     */
    public static void checkRelationshipEntityOwner(Map<String, PersistentEntity> owners, PersistentEntity node, PersistentEntity relationshipEntity) {
        PersistentEntity owner = owners.putIfAbsent(relationshipEntity.getName(), node);
        if (owner != null && !owner.getName().equals(node.getName())) {
            throw new IllegalStateException("Relationship entity [" + relationshipEntity.getName() + "] is used by the nodes ["
                + owner.getName() + "] and [" + node.getName() + "]: an edge table references the vertex table of a single node, so each node needs its own relationship entity");
        }
    }

    /**
     * The name of the edge table of a relationship to a node, without its schema: the table of the owner and the
     * relationship type, and the table of the target when the owner has the same type towards several nodes.
     *
     * @param owner       The node declaring the relationship
     * @param association The relationship
     * @return The table name
     */
    public static String nodeEdgeTableName(PersistentEntity owner, Association association) {
        String tableName = owner.getPersistedName() + "_" + GraphEntities.relationshipType(association);
        if (hasOtherNodeTarget(owner, association)) {
            tableName += "_" + association.getAssociatedEntity().getPersistedName();
        }
        return tableName;
    }

    /**
     * Renders the name of a table with its schema, quoted in upper case as the SQL query builder does for Oracle.
     *
     * @param schema The schema, or null
     * @param name   The table name
     * @return The rendered name
     */
    public static String qualifiedTableName(@Nullable String schema, String name) {
        return schema == null || schema.isEmpty() ? quote(name) : quote(schema) + "." + quote(name);
    }

    /**
     * Describes the edge table of a relationship.
     *
     * @param owner              The node declaring the relationship
     * @param association        The relationship
     * @param target             The node at the other end
     * @param table              The rendered name of the edge table
     * @param relationshipEntity Whether the edge table is that of a relationship entity
     * @return The edge table
     */
    public static EdgeTable edgeTable(PersistentEntity owner, Association association, PersistentEntity target, String table, boolean relationshipEntity) {
        // A relationship pointing at its owner is stored as an edge from the target to the owner
        boolean incoming = GraphEntities.direction(association) == Direction.INCOMING;
        return new EdgeTable(
            table,
            GraphEntities.relationshipType(association),
            incoming ? target : owner,
            incoming ? owner : target,
            owner.getName() + "." + association.getName(),
            relationshipEntity,
            incoming
        );
    }

    /**
     * Merges the edges of the relationships into the edge tables of the graph. Of the two sides of a relationship the
     * table of the first is kept: a relationship entity holds properties the other side has no column for, and
     * otherwise the table is named after the node the edge leaves.
     *
     * @param edges     The edges of every relationship
     * @param edgeTable The edge table of an edge
     * @param <E>       The type of the edges
     * @return The edges to create a table for
     * @throws IllegalStateException If two relationships conflict
     */
    public static <E> List<E> mergeEdges(List<E> edges, Function<E, EdgeTable> edgeTable) {
        List<E> sorted = new ArrayList<>(edges);
        sorted.sort(Comparator.comparing((E edge) -> !edgeTable.apply(edge).relationshipEntity())
            .thenComparing(edge -> edgeTable.apply(edge).incoming()));
        List<E> merged = new ArrayList<>();
        for (E edge : sorted) {
            if (isNew(merged, edgeTable, edgeTable.apply(edge))) {
                merged.add(edge);
            }
        }
        return merged;
    }

    private static <E> boolean isNew(List<E> merged, Function<E, EdgeTable> edgeTable, EdgeTable edge) {
        for (E existingEdge : merged) {
            EdgeTable existing = edgeTable.apply(existingEdge);
            boolean sameEnds = existing.label().equals(edge.label())
                && existing.source().getName().equals(edge.source().getName())
                && existing.destination().getName().equals(edge.destination().getName());
            if (existing.table().equals(edge.table())) {
                // Nothing in a row says which of the two relationships it belongs to
                if (!sameEnds) {
                    throw new IllegalStateException("Relationships [" + existing.declaredBy() + "] and [" + edge.declaredBy() + "] both map to the edge table "
                        + edge.table() + ", which can only hold one relationship type between one pair of nodes: use a relationship entity for each of them");
                }
                return false;
            }
            if (sameEnds) {
                if (existing.relationshipEntity() && edge.relationshipEntity()) {
                    throw new IllegalStateException("Relationships [" + existing.declaredBy() + "] and [" + edge.declaredBy() + "] are the two sides of one relationship with the edge tables "
                        + existing.table() + " and " + edge.table() + ": declare the relationship entity on one side only");
                }
                return false;
            }
        }
        return true;
    }

    /**
     * The nodes reachable from a root node through relationships, including those of relationship entities.
     *
     * @param root The root node
     * @return The nodes, starting with the root
     */
    public static Collection<PersistentEntity> reachableNodes(PersistentEntity root) {
        Map<String, PersistentEntity> nodes = new LinkedHashMap<>();
        Deque<PersistentEntity> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            PersistentEntity entity = pending.removeFirst();
            if (!entity.getAnnotationMetadata().hasStereotype(Node.class) || nodes.putIfAbsent(entity.getName(), entity) != null) {
                continue;
            }
            for (PersistentProperty property : entity.getPersistentProperties()) {
                if (property instanceof Association association && GraphEntities.isRelationship(association)) {
                    PersistentEntity target = targetNode(association);
                    if (target != null) {
                        pending.add(target);
                    }
                }
            }
        }
        return nodes.values();
    }

    private static @Nullable PersistentEntity targetNode(Association association) {
        PersistentEntity associated = association.getAssociatedEntity();
        if (GraphEntities.isRelationshipEntity(associated)) {
            Association targetNode = GraphEntities.findTargetNode(associated);
            return targetNode == null ? null : targetNode.getAssociatedEntity();
        }
        return associated;
    }

    private static boolean hasOtherNodeTarget(PersistentEntity owner, Association association) {
        String type = GraphEntities.relationshipType(association);
        String target = association.getAssociatedEntity().getName();
        for (PersistentProperty property : owner.getPersistentProperties()) {
            if (property instanceof Association other && GraphEntities.isRelationship(other)
                && !GraphEntities.isRelationshipEntity(other.getAssociatedEntity())
                && GraphEntities.relationshipType(other).equals(type)
                && !other.getAssociatedEntity().getName().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable String schemaName(PersistentEntity entity) {
        return entity.getAnnotationMetadata().stringValue(MappedEntity.class, SqlMembers.SCHEMA).orElse(null);
    }

    private static String quote(String name) {
        return '"' + name.toUpperCase(Locale.ENGLISH).replace("\"", "\"\"") + '"';
    }

    /**
     * The edge table of a relationship, with the ends of the edges it holds.
     *
     * @param table              The rendered name of the table, with its schema
     * @param label              The label of the edges, which is the relationship type
     * @param source             The node the edges leave
     * @param destination        The node the edges point at
     * @param declaredBy         The relationship, as the owner and property name
     * @param relationshipEntity Whether the table is that of a relationship entity
     * @param incoming           Whether the relationship points at the node declaring it
     */
    public record EdgeTable(String table,
                            String label,
                            PersistentEntity source,
                            PersistentEntity destination,
                            String declaredBy,
                            boolean relationshipEntity,
                            boolean incoming) {
    }
}
