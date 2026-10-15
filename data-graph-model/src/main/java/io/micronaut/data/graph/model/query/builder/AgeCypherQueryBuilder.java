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
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.QueryOutParameterBinding;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    // Every query is wrapped in the SQL statement that runs it, so nothing is parsed at runtime

    @Override
    public String finishQuery(String query) {
        return AgeStatements.wrap(query);
    }

    @Override
    public QueryResult buildSelect(AnnotationMetadata annotationMetadata, SelectQueryDefinition definition) {
        return wrap(super.buildSelect(annotationMetadata, definition));
    }

    @Override
    public QueryResult buildUpdate(AnnotationMetadata annotationMetadata, UpdateQueryDefinition definition) {
        return wrap(super.buildUpdate(annotationMetadata, definition));
    }

    @Override
    public QueryResult buildDelete(AnnotationMetadata annotationMetadata, DeleteQueryDefinition definition) {
        return wrap(super.buildDelete(annotationMetadata, definition));
    }

    @Override
    public QueryResult buildPathQuery(PathQueryDefinition definition) {
        return wrap(super.buildPathQuery(definition));
    }

    @Override
    public String buildFindByIdQuery(PersistentEntity entity) {
        return AgeStatements.wrap(super.buildFindByIdQuery(entity));
    }

    @Override
    public String buildFindAllQuery(PersistentEntity entity) {
        return AgeStatements.wrap(super.buildFindAllQuery(entity));
    }

    @Override
    public String buildCountAllQuery(PersistentEntity entity) {
        return AgeStatements.wrap(super.buildCountAllQuery(entity));
    }

    @Override
    public String buildWriteNodeQuery(PersistentEntity entity, List<String> propertyNames, boolean idPresent) {
        return AgeStatements.wrap(super.buildWriteNodeQuery(entity, propertyNames, idPresent));
    }

    @Override
    public String buildDeleteByIdQuery(PersistentEntity entity) {
        return AgeStatements.wrap(super.buildDeleteByIdQuery(entity));
    }

    @Override
    public String buildDeleteAllQuery(PersistentEntity entity) {
        return AgeStatements.wrap(super.buildDeleteAllQuery(entity));
    }

    @Override
    public String buildLinkQuery(Association association, PersistentEntity owner, PersistentEntity target,
                                 List<String> propertyNames, @Nullable PersistentProperty relationshipIdentity) {
        return AgeStatements.wrap(super.buildLinkQuery(association, owner, target, propertyNames, relationshipIdentity));
    }

    private static QueryResult wrap(QueryResult result) {
        return new WrappedQueryResult(AgeStatements.wrap(result.getQuery()), result);
    }

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
    protected void appendDynamicRelationships(StringBuilder query, PersistentEntity entity, List<String> columns) {
        if (GraphEntities.findDynamicRelationships(entity) != null) {
            throw new IllegalArgumentException("Dynamic relationships are not supported by Apache AGE, entity [" + entity.getName() + "] declares them");
        }
    }

    @Override
    public String buildDynamicLinkQuery(PersistentEntity owner, PersistentEntity target, String type) {
        throw new UnsupportedOperationException("Dynamic relationships are not supported by Apache AGE");
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

    /**
     * A query result whose query is the statement that runs it.
     *
     * @param query    The statement
     * @param delegate The result of the Cypher query
     */
    private record WrappedQueryResult(String query, QueryResult delegate) implements QueryResult {

        @Override
        public String getQuery() {
            return query;
        }

        @Override
        public List<String> getQueryParts() {
            return List.of(query);
        }

        @Override
        public @Nullable String getUpdate() {
            return delegate.getUpdate();
        }

        @Override
        public Map<String, String> getParameters() {
            return delegate.getParameters();
        }

        @Override
        public Map<String, DataType> getParameterTypes() {
            return delegate.getParameterTypes();
        }

        @Override
        public List<QueryParameterBinding> getParameterBindings() {
            return delegate.getParameterBindings();
        }

        @Override
        public List<QueryOutParameterBinding> getOutParameterBindings() {
            return delegate.getOutParameterBindings();
        }

        @Override
        public Map<String, String> getAdditionalRequiredParameters() {
            return delegate.getAdditionalRequiredParameters();
        }

        @Override
        public int getMax() {
            return delegate.getMax();
        }

        @Override
        public long getOffset() {
            return delegate.getOffset();
        }

        @Override
        public Sort getSort() {
            return delegate.getSort();
        }

        @Override
        public Collection<JoinPath> getJoinPaths() {
            return delegate.getJoinPaths();
        }
    }
}
