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
package io.micronaut.data.oracle.graph.schema;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.jdbc.config.JdbcSchemaContributor;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils;
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

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
 * Generates the tables and property graphs of the graph entities of an Oracle data source when schema generation is
 * enabled.
 *
 * <ul>
 *     <li>Each {@link Node} entity gets a vertex table, without columns for its relationships.</li>
 *     <li>Each relationship to a {@link io.micronaut.data.graph.annotation.RelationshipProperties} entity gets the
 *     table of that entity as its edge table, with a column referencing the node that owns the relationship.</li>
 *     <li>Each relationship to a node gets an edge table named after the table of its owner and the relationship
 *     type, with columns referencing the owner and the target.</li>
 *     <li>Each graph named by an {@link OracleGraphRepository} of the data source is created with
 *     {@code CREATE PROPERTY GRAPH} over the vertex and edge tables reachable from the entities of its repositories.</li>
 * </ul>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Singleton
@Internal
final class OraclePropertyGraphSchemaContributor implements JdbcSchemaContributor {

    private final BeanContext beanContext;

    OraclePropertyGraphSchemaContributor(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public List<PersistentEntity> selectEntities(DataJdbcConfiguration configuration, List<PersistentEntity> entities) {
        if (configuration.getDialect() != Dialect.ORACLE) {
            return List.of();
        }
        return entities.stream()
            .filter(entity -> isNode(entity) || GraphEntities.isRelationshipEntity(entity))
            .toList();
    }

    @Override
    public List<String> buildCreateStatements(DataJdbcConfiguration configuration, List<PersistentEntity> entities) {
        Schema schema = schema(entities);
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE, configuration.getDialectOptions().getVersion());
        List<String> statements = new ArrayList<>();
        for (Vertex vertex : schema.vertices.values()) {
            statements.addAll(List.of(builder.buildCreateTableStatements(vertex.table)));
        }
        for (Edge edge : schema.edges) {
            statements.addAll(List.of(builder.buildCreateTableStatements(edge.table)));
        }
        for (Map.Entry<String, Set<PersistentEntity>> graph : graphs(configuration, schema).entrySet()) {
            statements.add(createPropertyGraph(graph.getKey(), graph.getValue(), schema));
        }
        return statements;
    }

    @Override
    public List<String> buildDropStatements(DataJdbcConfiguration configuration, List<PersistentEntity> entities) {
        Schema schema = schema(entities);
        List<String> statements = new ArrayList<>();
        for (String graph : graphs(configuration, schema).keySet()) {
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

    private Schema schema(List<PersistentEntity> entities) {
        Schema schema = new Schema();
        for (PersistentEntity entity : entities) {
            if (isNode(entity)) {
                schema.vertices.put(entity.getName(), vertex(entity));
            }
        }
        Map<String, PersistentEntity> edgeOwners = new LinkedHashMap<>();
        for (PersistentEntity entity : entities) {
            if (!isNode(entity)) {
                continue;
            }
            for (PersistentProperty property : entity.getPersistentProperties()) {
                if (!(property instanceof Association association) || !GraphEntities.isRelationship(association)) {
                    continue;
                }
                PersistentEntity associated = association.getAssociatedEntity();
                if (GraphEntities.isRelationshipEntity(associated)) {
                    PersistentEntity owner = edgeOwners.putIfAbsent(associated.getName(), entity);
                    if (owner != null && !owner.getName().equals(entity.getName())) {
                        throw new IllegalStateException("Relationship entity [" + associated.getName() + "] is used by the nodes ["
                            + owner.getName() + "] and [" + entity.getName() + "]: an edge table references the vertex table of a single node, so each node needs its own relationship entity");
                    }
                    schema.add(relationshipEntityEdge(entity, association));
                } else {
                    schema.add(nodeEdge(entity, association));
                }
            }
        }
        return schema;
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
        return edge(owner, association, targetNode.getAssociatedEntity(),
            withColumns(table, table.name(), table.primaryKeyColumns(), columns),
            table.primaryKeyColumns().stream().map(SqlColumnMapping::getName).toList(),
            ownerColumn, targetColumn, properties);
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
        return edge(owner, association, target, table, List.of(ownerColumn, targetColumn), ownerColumn, targetColumn, List.of());
    }

    private static Edge edge(PersistentEntity owner,
                             Association association,
                             PersistentEntity target,
                             SqlTableMapping table,
                             List<String> keyColumns,
                             String ownerColumn,
                             String targetColumn,
                             List<String> properties) {
        // A relationship pointing at its owner is stored as an edge from the target to the owner
        boolean incoming = GraphEntities.direction(association) == Direction.INCOMING;
        return new Edge(
            table,
            keyColumns,
            incoming ? target : owner,
            incoming ? targetColumn : ownerColumn,
            incoming ? owner : target,
            incoming ? ownerColumn : targetColumn,
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
        return new SqlTableMapping(table.schema(), name, table.escape(), SqlTableMapping.TableType.MAIN, primaryKeyColumns, columns,
            table.sequences(), table.indexes(), table.auxiliaryStatements());
    }

    private Map<String, Set<PersistentEntity>> graphs(DataJdbcConfiguration configuration, Schema schema) {
        Map<String, PersistentEntity> vertices = schema.vertices.values().stream()
            .collect(Collectors.toMap(vertex -> vertex.entity.getName(), Vertex::entity, (a, b) -> a, LinkedHashMap::new));
        Map<String, Set<PersistentEntity>> graphs = new LinkedHashMap<>();
        Collection<BeanDefinition<Object>> repositories = beanContext.getBeanDefinitions(Qualifiers.byStereotype(OracleGraphRepository.class));
        for (BeanDefinition<?> repository : repositories) {
            String dataSource = repository.stringValue(Repository.class).orElse("default");
            String graph = repository.stringValue(PropertyGraph.class).orElse(null);
            if (graph == null || !dataSource.equals(configuration.getName())) {
                continue;
            }
            List<Argument<?>> typeArguments = repository.getTypeArguments(GenericRepository.class);
            if (typeArguments.isEmpty()) {
                continue;
            }
            PersistentEntity root = vertices.get(typeArguments.get(0).getType().getName());
            if (root != null) {
                reachable(root, vertices, graphs.computeIfAbsent(graph, name -> new LinkedHashSet<>()));
            }
        }
        return graphs;
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
                + " KEY (" + edge.keyColumns.stream().map(OraclePropertyGraphSchemaContributor::quote).collect(Collectors.joining(", ")) + ")"
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
        return "PROPERTIES (" + columns.stream().map(OraclePropertyGraphSchemaContributor::quote).collect(Collectors.joining(", ")) + ")";
    }

    /**
     * Quotes a table or column name the way the SQL query builder does for Oracle: in upper case.
     */
    private static String quote(String name) {
        return '"' + name.toUpperCase(Locale.ENGLISH) + '"';
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
