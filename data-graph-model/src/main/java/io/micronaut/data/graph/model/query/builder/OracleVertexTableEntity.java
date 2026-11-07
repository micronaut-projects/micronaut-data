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
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The columns of the vertex table of a node. A single relationship is an association with a foreign key to the SQL
 * query builder, but it is stored in an edge table, so the columns leave it out. It can still be navigated by name.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class OracleVertexTableEntity implements PersistentEntity {

    private final PersistentEntity entity;
    private final List<PersistentProperty> columns;

    private OracleVertexTableEntity(PersistentEntity entity, List<PersistentProperty> columns) {
        this.entity = entity;
        this.columns = columns;
    }

    /**
     * @param entity The entity
     * @return The entity to write the vertex table with, which is the entity itself without a single relationship
     */
    static PersistentEntity of(PersistentEntity entity) {
        Collection<? extends PersistentProperty> properties = entity.getPersistentProperties();
        List<PersistentProperty> columns = properties.stream()
            .filter(property -> !GraphEntities.isSingleRelationship(property))
            .map(PersistentProperty.class::cast)
            .toList();
        return columns.size() == properties.size() ? entity : new OracleVertexTableEntity(entity, columns);
    }

    @Override
    public Collection<? extends PersistentProperty> getPersistentProperties() {
        return columns;
    }

    @Override
    public List<String> getPersistentPropertyNames() {
        return columns.stream().map(PersistentProperty::getName).toList();
    }

    @Override
    public String getName() {
        return entity.getName();
    }

    @Override
    public String getPersistedName() {
        return entity.getPersistedName();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return entity.getAnnotationMetadata();
    }

    @Override
    public String getAliasName() {
        return entity.getAliasName();
    }

    @Override
    public boolean hasCompositeIdentity() {
        return entity.hasCompositeIdentity();
    }

    @Override
    public boolean hasIdentity() {
        return entity.hasIdentity();
    }

    @Override
    public PersistentProperty[] getCompositeIdentity() {
        return entity.getCompositeIdentity();
    }

    @Override
    public PersistentProperty getIdentity() {
        return entity.getIdentity();
    }

    @Override
    public PersistentProperty getVersion() {
        return entity.getVersion();
    }

    @Override
    public boolean hasVersion() {
        return entity.hasVersion();
    }

    @Override
    public @Nullable PersistentProperty getPropertyByName(String name) {
        return entity.getPropertyByName(name);
    }

    @Override
    public @Nullable PersistentProperty getPropertyByNameIgnoreCase(String name) {
        return entity.getPropertyByNameIgnoreCase(name);
    }

    @Override
    public boolean isOwningEntity(PersistentEntity owner) {
        return entity.isOwningEntity(owner);
    }

    @Override
    public @Nullable PersistentEntity getParentEntity() {
        return entity.getParentEntity();
    }

    @Override
    public NamingStrategy getNamingStrategy() {
        return entity.getNamingStrategy();
    }

    @Override
    public Optional<NamingStrategy> findNamingStrategy() {
        return entity.findNamingStrategy();
    }
}
