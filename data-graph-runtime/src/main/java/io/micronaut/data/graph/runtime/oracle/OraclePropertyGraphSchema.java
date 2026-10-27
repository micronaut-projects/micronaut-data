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
package io.micronaut.data.graph.runtime.oracle;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils;
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.SqlIndexMapping;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the DDL of the tables and property graphs of the graph entities of an Oracle data source, for the schema
 * generators of the JDBC and R2DBC drivers.
 *
 * <ul>
 *     <li>Each {@link Node} entity gets a vertex table, without columns for its relationships.</li>
 *     <li>Each relationship to a {@link io.micronaut.data.graph.annotation.RelationshipProperties} entity gets the
 *     table of that entity as its edge table, with a column referencing the node that owns the relationship.</li>
 *     <li>Each relationship to a node gets an edge table named after the table of its owner and the relationship
 *     type, with columns referencing the owner and the target.</li>
 *     <li>Each graph named by a graph repository of the data source is created with {@code CREATE PROPERTY GRAPH}
 *     over the vertex and edge tables reachable from the entities of its repositories.</li>
 * </ul>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class OraclePropertyGraphSchema {

    private final Schema schema;
    private final Map<String, Set<PersistentEntity>> graphs;

    private OraclePropertyGraphSchema(Schema schema, Map<String, Set<PersistentEntity>> graphs) {
        this.schema = schema;
        this.graphs = graphs;
    }

    /**
     * Selects the graph entities of a data source: its nodes and relationship entities.
     *
     * @param dialect  The dialect of the data source
     * @param entities The entities of the data source
     * @return The graph entities, empty if the data source is not an Oracle data source
     */
    public static List<PersistentEntity> selectEntities(Dialect dialect, List<PersistentEntity> entities) {
        if (dialect != Dialect.ORACLE) {
            return List.of();
        }
        return entities.stream()
            .filter(entity -> isNode(entity) || GraphEntities.isRelationshipEntity(entity))
            .toList();
    }

    /**
     * Maps graph entities to the tables and property graphs of a data source.
     *
     * @param beanContext          The bean context, to find the graph repositories of the data source
     * @param repositoryStereotype The stereotype of the graph repositories of the driver
     * @param dataSource           The name of the data source
     * @param entities             The graph entities, as returned by {@link #selectEntities(Dialect, List)}
     * @return The schema
     */
    public static OraclePropertyGraphSchema of(BeanContext beanContext,
                                               Class<? extends Annotation> repositoryStereotype,
                                               String dataSource,
                                               List<PersistentEntity> entities) {
        Schema schema = schema(entities);
        return new OraclePropertyGraphSchema(schema, graphs(beanContext, repositoryStereotype, dataSource, schema));
    }

    /**
     * Builds the statements that create the vertex and edge tables, then the property graphs.
     *
     * @param dialectVersion The version of the Oracle dialect
     * @return The statements, run in order
     */
    public List<String> buildCreateStatements(@Nullable String dialectVersion) {
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE, dialectVersion);
        List<String> statements = new ArrayList<>();
        for (Vertex vertex : schema.vertices.values()) {
            statements.addAll(List.of(builder.buildCreateTableStatements(vertex.table)));
        }
        for (Edge edge : schema.edges) {
            statements.addAll(List.of(builder.buildCreateTableStatements(edge.table)));
        }
        for (Map.Entry<String, Set<PersistentEntity>> graph : graphs.entrySet()) {
            statements.add(createPropertyGraph(graph.getKey(), graph.getValue(), schema));
        }
        return statements;
    }

    /**
     * Builds the statements that drop the property graphs, then the edge and vertex tables.
     *
     * @return The statements, run in order
     */
    public List<String> buildDropStatements() {
        List<String> statements = new ArrayList<>();
        for (String graph : graphs.keySet()) {
            statements.add("DROP PROPERTY GRAPH " + identifier(graph));
        }
        for (Edge edge : schema.edges) {
            statements.add("DROP TABLE " + quote(edge.table.name()));
        }
        for (Vertex vertex : schema.vertices.values()) {
            statements.add("DROP TABLE " + quote(vertex.table.name()));
        }
        return statements;
    }

    private static boolean isNode(PersistentEntity entity) {
        return entity.getAnnotationMetadata().hasStereotype(Node.class);
    }

    private static Schema schema(List<PersistentEntity> entities) {
        Schema schema = new Schema();
        for (PersistentEntity entity : entities) {
            if (isNode(entity)) {
                schema.vertices.put(entity.getName(), vertex(entity));
            }
        }
        Map<String, PersistentEntity> edgeOwners = new LinkedHashMap<>();
        for (PersistentEntity entity : entities) {
            if (isNode(entity)) {
                addEdges(schema, entity, edgeOwners);
            }
        }
        return schema;
    }

    private static void addEdges(Schema schema, PersistentEntity node, Map<String, PersistentEntity> edgeOwners) {
        for (PersistentProperty property : node.getPersistentProperties()) {
            if (!(property instanceof Association association) || !GraphEntities.isRelationship(association)) {
                continue;
            }
            PersistentEntity associated = association.getAssociatedEntity();
            if (GraphEntities.isRelationshipEntity(associated)) {
                PersistentEntity owner = edgeOwners.putIfAbsent(associated.getName(), node);
                if (owner != null && !owner.getName().equals(node.getName())) {
                    throw new IllegalStateException("Relationship entity [" + associated.getName() + "] is used by the nodes ["
                        + owner.getName() + "] and [" + node.getName() + "]: an edge table references the vertex table of a single node, so each node needs its own relationship entity");
                }
                schema.add(relationshipEntityEdge(node, association));
            } else {
                schema.add(nodeEdge(node, association));
            }
        }
    }

    private static Vertex vertex(PersistentEntity entity) {
        SqlTableMapping table = mainTable(entity);
        Set<String> relationshipColumns = new HashSet<>();
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property instanceof Association association && GraphEntities.isRelationship(association)) {
                relationshipColumns.addAll(columnNames(entity, association));
            }
        }
        List<SqlColumnMapping> columns = table.columns().stream()
            .filter(column -> !relationshipColumns.contains(column.getName()))
            .toList();
        return new Vertex(entity, withColumns(table, table.name(), table.primaryKeyColumns(), columns));
    }

    private static Edge relationshipEntityEdge(PersistentEntity owner, Association association) {
        PersistentEntity relationshipEntity = association.getAssociatedEntity();
        Association targetNode = GraphEntities.findTargetNode(relationshipEntity);
        if (targetNode == null) {
            throw new IllegalStateException("Relationship entity [" + relationshipEntity.getName() + "] has no @TargetNode property");
        }
        SqlTableMapping table = mainTable(relationshipEntity);
        if (table.primaryKeyColumns().isEmpty()) {
            throw new IllegalStateException("Relationship entity [" + relationshipEntity.getName()
                + "] has no @RelationshipId property: its table is an edge table keyed by the relationship identifier");
        }
        String targetColumn = singleColumn(relationshipEntity, targetNode);
        Set<String> columnNames = table.columns().stream().map(SqlColumnMapping::getName).collect(Collectors.toSet());
        table.primaryKeyColumns().forEach(column -> columnNames.add(column.getName()));
        String ownerColumn = referenceColumnName(relationshipEntity, NameUtils.decapitalize(owner.getSimpleName()));
        if (columnNames.contains(ownerColumn)) {
            ownerColumn = referenceColumnName(relationshipEntity, "owner");
        }
        List<SqlColumnMapping> columns = new ArrayList<>(table.columns());
        columns.add(identityColumn(owner).asReference(ownerColumn));
        List<String> properties = new ArrayList<>();
        table.primaryKeyColumns().forEach(column -> properties.add(column.getName()));
        table.columns().stream()
            .map(SqlColumnMapping::getName)
            .filter(name -> !name.equals(targetColumn))
            .forEach(properties::add);
        // The relationship identifier is the key of the edge table, so two relationships may link the same pair of
        // nodes, such as a person playing two roles in a movie; the pair is indexed for the traversals
        List<SqlIndexMapping> indexes = new ArrayList<>(table.indexes());
        // The name is derived from the table and the columns, as for the indexes of the entity
        indexes.add(new SqlIndexMapping("", false, new String[] {ownerColumn, targetColumn}));
        return edge(owner, association, targetNode.getAssociatedEntity(),
            withColumns(table, table.name(), table.primaryKeyColumns(), columns, indexes),
            new EdgeKeys(table.primaryKeyColumns().stream().map(SqlColumnMapping::getName).toList(), ownerColumn, targetColumn),
            properties);
    }

    private static Edge nodeEdge(PersistentEntity owner, Association association) {
        PersistentEntity target = association.getAssociatedEntity();
        NamingStrategy namingStrategy = owner.getNamingStrategy();
        String tableName = mainTable(owner).name() + "_" + GraphEntities.relationshipType(association);
        String ownerColumn = referenceColumnName(owner, NameUtils.decapitalize(owner.getSimpleName()));
        String targetColumn = namingStrategy.mappedName(association.getName() + "Id");
        if (targetColumn.equals(ownerColumn)) {
            targetColumn = namingStrategy.mappedName("targetId");
        }
        List<SqlColumnMapping> keys = List.of(
            identityColumn(owner).asReference(ownerColumn),
            identityColumn(target).asReference(targetColumn)
        );
        SqlTableMapping table = new SqlTableMapping(null, tableName, true, SqlTableMapping.TableType.MAIN, keys, List.of());
        return edge(owner, association, target, table, new EdgeKeys(List.of(ownerColumn, targetColumn), ownerColumn, targetColumn), List.of());
    }

    private static Edge edge(PersistentEntity owner,
                             Association association,
                             PersistentEntity target,
                             SqlTableMapping table,
                             EdgeKeys keys,
                             List<String> properties) {
        // A relationship pointing at its owner is stored as an edge from the target to the owner
        boolean incoming = GraphEntities.direction(association) == Direction.INCOMING;
        return new Edge(
            table,
            keys.keyColumns(),
            incoming ? target : owner,
            incoming ? keys.targetColumn() : keys.ownerColumn(),
            incoming ? owner : target,
            incoming ? keys.ownerColumn() : keys.targetColumn(),
            GraphEntities.relationshipType(association),
            properties
        );
    }

    private static String referenceColumnName(PersistentEntity entity, String name) {
        return entity.getNamingStrategy().mappedName(name + "Id");
    }

    private static SqlTableMapping mainTable(PersistentEntity entity) {
        return SqlSchemaUtils.getSqlTableMappings(entity, Dialect.ORACLE).stream()
            .filter(table -> table.type() == SqlTableMapping.TableType.MAIN)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No table mapped for entity [" + entity.getName() + "]"));
    }

    private static SqlColumnMapping identityColumn(PersistentEntity entity) {
        List<SqlColumnMapping> keys = mainTable(entity).primaryKeyColumns();
        if (keys.size() != 1) {
            throw new IllegalStateException("Property graph schema generation needs a single identifier column for entity [" + entity.getName() + "]");
        }
        return keys.get(0);
    }

    private static List<String> columnNames(PersistentEntity entity, Association association) {
        NamingStrategy namingStrategy = entity.getNamingStrategy();
        List<String> names = new ArrayList<>();
        PersistentEntityUtils.traversePersistentProperties(List.of(), association,
            (associations, property) -> names.add(namingStrategy.mappedName(associations, property)));
        return names;
    }

    private static String singleColumn(PersistentEntity entity, Association association) {
        List<String> names = columnNames(entity, association);
        if (names.size() != 1) {
            throw new IllegalStateException("Property graph schema generation needs a single column for [" + entity.getName() + "." + association.getName() + "]");
        }
        return names.get(0);
    }

    private static SqlTableMapping withColumns(SqlTableMapping table, String name, List<SqlColumnMapping> primaryKeyColumns, List<SqlColumnMapping> columns) {
        return withColumns(table, name, primaryKeyColumns, columns, table.indexes());
    }

    private static SqlTableMapping withColumns(SqlTableMapping table, String name, List<SqlColumnMapping> primaryKeyColumns,
                                               List<SqlColumnMapping> columns, List<SqlIndexMapping> indexes) {
        return new SqlTableMapping(table.schema(), name, table.escape(), SqlTableMapping.TableType.MAIN, primaryKeyColumns, columns,
            table.sequences(), indexes, table.auxiliaryStatements());
    }

    private static Map<String, Set<PersistentEntity>> graphs(BeanContext beanContext,
                                                             Class<? extends Annotation> repositoryStereotype,
                                                             String dataSourceName,
                                                             Schema schema) {
        Map<String, PersistentEntity> vertices = schema.vertices.values().stream()
            .collect(Collectors.toMap(vertex -> vertex.entity.getName(), Vertex::entity, (a, b) -> a, LinkedHashMap::new));
        Map<String, Set<PersistentEntity>> graphs = new LinkedHashMap<>();
        Collection<BeanDefinition<Object>> repositories = beanContext.getBeanDefinitions(Qualifiers.byStereotype(repositoryStereotype));
        for (BeanDefinition<?> repository : repositories) {
            String dataSource = repository.stringValue(Repository.class).orElse("default");
            String graph = repository.stringValue(PropertyGraph.class).orElse(null);
            PersistentEntity root = graph != null && dataSource.equals(dataSourceName) ? rootVertex(repository, vertices) : null;
            if (root != null) {
                reachable(root, vertices, graphs.computeIfAbsent(graph, name -> new LinkedHashSet<>()));
            }
        }
        return graphs;
    }

    private static @Nullable PersistentEntity rootVertex(BeanDefinition<?> repository, Map<String, PersistentEntity> vertices) {
        List<Argument<?>> typeArguments = repository.getTypeArguments(GenericRepository.class);
        return typeArguments.isEmpty() ? null : vertices.get(typeArguments.get(0).getType().getName());
    }

    private static void reachable(PersistentEntity root, Map<String, PersistentEntity> vertices, Set<PersistentEntity> graph) {
        Deque<PersistentEntity> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            PersistentEntity entity = pending.removeFirst();
            if (!graph.add(entity)) {
                continue;
            }
            for (PersistentProperty property : entity.getPersistentProperties()) {
                if (property instanceof Association association && GraphEntities.isRelationship(association)) {
                    PersistentEntity target = vertices.get(GraphEntities.targetNodeEntity(association).getName());
                    if (target != null) {
                        pending.add(target);
                    }
                }
            }
        }
    }

    private static String createPropertyGraph(String name, Set<PersistentEntity> vertices, Schema schema) {
        Set<String> names = vertices.stream().map(PersistentEntity::getName).collect(Collectors.toSet());
        Function<PersistentEntity, String> tableOf = entity -> quote(schema.vertex(entity).table.name());
        Function<PersistentEntity, String> keyOf = entity -> quote(identityColumn(entity).getName());
        StringBuilder sql = new StringBuilder("CREATE PROPERTY GRAPH ").append(identifier(name)).append(" VERTEX TABLES (");
        List<String> vertexTables = new ArrayList<>();
        for (PersistentEntity entity : vertices) {
            SqlTableMapping table = schema.vertex(entity).table;
            List<String> properties = new ArrayList<>();
            table.primaryKeyColumns().forEach(column -> properties.add(column.getName()));
            table.columns().forEach(column -> properties.add(column.getName()));
            StringBuilder vertex = new StringBuilder(quote(table.name()))
                .append(" KEY (").append(keyOf.apply(entity)).append(')');
            for (String label : GraphEntities.labels(entity)) {
                vertex.append(" LABEL ").append(identifier(label)).append(' ').append(properties(properties));
            }
            vertexTables.add(vertex.toString());
        }
        sql.append(String.join(", ", vertexTables)).append(')');
        List<String> edgeTables = new ArrayList<>();
        for (Edge edge : schema.edges) {
            if (!names.contains(edge.source.getName()) || !names.contains(edge.destination.getName())) {
                continue;
            }
            edgeTables.add(quote(edge.table.name())
                + " KEY (" + edge.keyColumns.stream().map(OraclePropertyGraphSchema::quote).collect(Collectors.joining(", ")) + ")"
                + " SOURCE KEY (" + quote(edge.sourceColumn) + ") REFERENCES " + tableOf.apply(edge.source) + " (" + keyOf.apply(edge.source) + ")"
                + " DESTINATION KEY (" + quote(edge.destinationColumn) + ") REFERENCES " + tableOf.apply(edge.destination) + " (" + keyOf.apply(edge.destination) + ")"
                + " LABEL " + identifier(edge.label) + " " + properties(edge.properties));
        }
        if (!edgeTables.isEmpty()) {
            sql.append(" EDGE TABLES (").append(String.join(", ", edgeTables)).append(')');
        }
        return sql.toString();
    }

    private static String properties(List<String> columns) {
        if (columns.isEmpty()) {
            return "NO PROPERTIES";
        }
        return "PROPERTIES (" + columns.stream().map(OraclePropertyGraphSchema::quote).collect(Collectors.joining(", ")) + ")";
    }

    /**
     * Quotes a table or column name the way the SQL query builder does for Oracle: in upper case.
     */
    private static String quote(String name) {
        return '"' + GraphEntities.requireName(name).toUpperCase(Locale.ENGLISH).replace("\"", "\"\"") + '"';
    }

    /**
     * Renders a graph name or label. Graph queries write plain names unquoted, which Oracle folds to upper case, so
     * plain names are left unquoted here too.
     */
    private static String identifier(String name) {
        String escaped = GraphEntities.escapeName(name);
        return escaped.equals(name) ? name : '"' + name.replace("\"", "\"\"") + '"';
    }

    private static final class Schema {
        private final Map<String, Vertex> vertices = new LinkedHashMap<>();
        private final List<Edge> edges = new ArrayList<>();

        private Vertex vertex(PersistentEntity entity) {
            Vertex vertex = vertices.get(entity.getName());
            if (vertex == null) {
                throw new IllegalStateException("No vertex table for entity [" + entity.getName() + "]");
            }
            return vertex;
        }

        private void add(Edge edge) {
            // Two relationships of a node can share a relationship entity, and so its table
            if (edges.stream().noneMatch(existing -> existing.table.name().equals(edge.table.name()))) {
                edges.add(edge);
            }
        }
    }

    private record Vertex(PersistentEntity entity, SqlTableMapping table) {
    }

    private record EdgeKeys(List<String> keyColumns, String ownerColumn, String targetColumn) {
    }

    private record Edge(SqlTableMapping table,
                        List<String> keyColumns,
                        PersistentEntity source,
                        String sourceColumn,
                        PersistentEntity destination,
                        String destinationColumn,
                        String label,
                        List<String> properties) {
    }
}
