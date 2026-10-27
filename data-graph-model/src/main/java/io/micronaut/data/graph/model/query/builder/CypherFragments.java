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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.model.Association;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fragments of Cypher text shared by the clauses of {@link CypherQueryBuilder}.
 */
@Internal
final class CypherFragments {

    private CypherFragments() {
    }

    static String orderByClause(List<OrderItem> orders) {
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

    static List<String> appendDistinctRoot(StringBuilder query, List<OrderItem> orders) {
        List<String> carried = new ArrayList<>();
        for (OrderItem order : orders) {
            if (order.onAssociation) {
                if (carried.isEmpty()) {
                    query.append(CypherQueryBuilder.WITH).append(CypherQueryBuilder.ROOT);
                }
                String alias = "o" + carried.size();
                query.append(", ").append(order.ascending ? "min(" : "max(").append(order.expression).append(CypherQueryBuilder.CLOSE_AS).append(alias);
                order.alias = alias;
                carried.add(alias);
            }
        }
        if (carried.isEmpty()) {
            query.append(CypherQueryBuilder.WITH_DISTINCT).append(CypherQueryBuilder.ROOT);
        }
        return carried;
    }

    static String relationshipTypes(List<Association> associations) {
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

    static void appendWhere(StringBuilder query, List<String> conditions) {
        if (!conditions.isEmpty()) {
            query.append(CypherQueryBuilder.WHERE).append(String.join(CypherQueryBuilder.AND, conditions));
        }
    }

    static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    static boolean isTargetNode(Association association) {
        return association.getAnnotationMetadata().hasStereotype("io.micronaut.data.graph.annotation.TargetNode");
    }
}
