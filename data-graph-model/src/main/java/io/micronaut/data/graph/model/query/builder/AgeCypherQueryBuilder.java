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

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.model.query.PathQueryDefinition;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;

import java.util.List;

/**
 * Builds the openCypher subset of Apache AGE.
 *
 * <ul>
 *     <li>A node has exactly one label.</li>
 *     <li>Identifiers assigned by the database are graph ids, read with {@code id()}.</li>
 *     <li>There is no {@code shortestPath()}: shortest paths are the shortest of all paths up to the
 *     maximum depth, which every path query therefore needs.</li>
 * </ul>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Introspected
public class AgeCypherQueryBuilder extends CypherQueryBuilder {

    @Override
    protected String identityExpression(PersistentProperty identity, String variable) {
        if (GraphEntities.isNativeId(identity)) {
            return "id(" + variable + ")";
        }
        return super.identityExpression(identity, variable);
    }

    @Override
    protected String labelExpression(PersistentEntity entity) {
        List<String> labels = GraphEntities.labels(entity);
        if (labels.size() > 1) {
            throw new IllegalArgumentException("Apache AGE nodes have a single label, entity [" + entity.getName() + "] declares " + labels);
        }
        return ":" + GraphEntities.escapeName(labels.get(0));
    }

    @Override
    public String buildAddLabelsQuery(PersistentEntity entity, List<String> labels) {
        throw new UnsupportedOperationException("Apache AGE nodes have a single label: dynamic labels are not supported");
    }

    @Override
    protected boolean supportsUnboundedPaths() {
        return false;
    }

    @Override
    protected void appendPathMatch(StringBuilder query, PathQueryDefinition.Kind kind, String relationship, Direction direction) {
        String pattern = directed("(" + PATH_START + ")", relationship, "(" + PATH_END + ")", direction);
        if (kind == PathQueryDefinition.Kind.ALL_SHORTEST) {
            query.append(" MATCH ").append(PATH).append(" = ").append(pattern)
                .append(" WITH ").append(PATH_START).append(", ").append(PATH_END)
                .append(", min(length(").append(PATH).append(")) AS shortest");
            query.append(" MATCH ").append(PATH).append(" = ").append(pattern)
                .append(" WHERE length(").append(PATH).append(") = shortest");
        } else {
            query.append(" MATCH ").append(PATH).append(" = ").append(pattern);
        }
    }

    @Override
    protected void appendPathReturn(StringBuilder query, PathQueryDefinition definition) {
        if (definition.kind() == PathQueryDefinition.Kind.SHORTEST && definition.result() == PathQueryDefinition.Result.ELEMENTS) {
            query.append(" RETURN ").append(PATH).append(" ORDER BY length(").append(PATH).append(") LIMIT 1");
            return;
        }
        super.appendPathReturn(query, definition);
    }
}
