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
package io.micronaut.data.model.query.builder.sql.postgres;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Primary-key reload SQL and notification key mappings shared by annotation processing and runtime fallback.
 *
 * @param query the mapped SELECT query
 * @param keys primary-key columns and property paths in query parameter order
 */
@Internal
public record PostgresNotificationQuery(String query, List<Key> keys) {
    /** Creates a query with immutable, ordered key mappings. */
    public PostgresNotificationQuery {
        keys = List.copyOf(keys);
    }

    /**
     * Builds a reload using the query builder's existing scalar, composite, and embedded ID support.
     *
     * @param entity the entity mapping
     * @param builder the compile-time or runtime criteria builder
     * @return the reload SQL and key mappings in binding order
     * @throws IllegalArgumentException if a key mapping or an additional query parameter is unsupported
     */
    public static PostgresNotificationQuery build(PersistentEntity entity, PersistentEntityCriteriaBuilder builder) {
        Map<String, Key> remaining = keyMappings(entity);
        var query = builder.createQuery();
        var root = query.from(entity);
        query.select(root).where(builder.equal(root.id(), builder.parameter(Object.class, "id")));
        var result = Objects.requireNonNull(query.build(AnnotationMetadata.EMPTY_METADATA, new SqlQueryBuilder(Dialect.POSTGRES)));
        var bindings = result.getParameterBindings();
        if (bindings.size() != remaining.size()) {
            throw new IllegalArgumentException("cannot reload entities with additional query parameters; "
                + "parameterized entity @Where clauses are not supported");
        }
        List<Key> ordered = new ArrayList<>(bindings.size());
        for (var binding : bindings) {
            String[] path = binding.getPropertyPath();
            Key key = path == null ? null : remaining.remove(String.join(".", path));
            if (key == null) {
                throw new IllegalArgumentException("requires only primary-key reload parameters");
            }
            ordered.add(key);
        }
        return new PostgresNotificationQuery(result.getQuery(), ordered);
    }

    /**
     * Resolves trigger columns without building a query or requiring repository operations.
     *
     * @param entity the entity mapping
     * @return the validated primary-key column names
     * @throws IllegalArgumentException if the key mapping is unsupported
     */
    public static List<String> keyColumns(PersistentEntity entity) {
        return keyMappings(entity).values().stream().map(Key::column).toList();
    }

    private static Map<String, Key> keyMappings(PersistentEntity entity) {
        Map<String, Key> keys = new LinkedHashMap<>();
        Set<String> columns = new HashSet<>();
        boolean quoted = entity.getAnnotationMetadata().booleanValue(MappedEntity.class, "escape").orElse(true);
        entity.getIdentityProperties().forEach(id -> PersistentEntityUtils.traversePersistentProperties(id, (associations, property) -> {
            if (associations.stream().anyMatch(association -> !(association instanceof Embedded))
                || property.getAnnotationMetadata().hasStereotype(Relation.class)
                || property.getAnnotationMetadata().stringValue(MappedProperty.class, "converter").isPresent()) {
                throw new IllegalArgumentException("does not support association or converted primary keys");
            }
            String column = PostgresIdentifiers.normalize(entity.getNamingStrategy().mappedName(associations, property), quoted);
            if (column.isEmpty() || !columns.add(column)) {
                throw new IllegalArgumentException("has an empty or duplicate PostgreSQL key column [" + column + "]");
            }
            var path = new PersistentPropertyPath(associations, property);
            keys.put(path.getPath(), new Key(column, path));
        }));
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("requires a mapped primary key");
        }
        return keys;
    }

    /**
     * A mapped notification key shared by query generation and trigger installation.
     *
     * @param column the PostgreSQL column name
     * @param path the mapped property path
     */
    @Internal
    public record Key(String column, PersistentPropertyPath path) {
    }
}
