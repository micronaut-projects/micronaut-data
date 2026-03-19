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
package io.micronaut.data.processor.model.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.IdentifiableType;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Internal
final class SourcePersistentEntityType<T> implements EntityType<T> {

    private final SourcePersistentEntity persistentEntity;

    SourcePersistentEntityType(SourcePersistentEntity persistentEntity) {
        this.persistentEntity = persistentEntity;
    }

    @Override
    public String getName() {
        return persistentEntity.getDecapitalizedName();
    }

    @Override
    public PersistenceType getPersistenceType() {
        return PersistenceType.ENTITY;
    }

    @Override
    public Class<T> getJavaType() {
        throw notSupportedOperation();
    }

    @Override
    public BindableType getBindableType() {
        return BindableType.ENTITY_TYPE;
    }

    @Override
    public Class<T> getBindableJavaType() {
        return getJavaType();
    }

    @Override
    public Type<?> getIdType() {
        throw notSupportedOperation();
    }

    @Override
    public <Y> SingularAttribute<? super T, Y> getId(Class<Y> type) {
        return (SingularAttribute<? super T, Y>) new SourcePersistentPropertySingularAttribute<>(persistentEntity.getIdentity());
    }

    @Override
    public <Y> SingularAttribute<T, Y> getDeclaredId(Class<Y> type) {
        return (SingularAttribute<T, Y>) getId(type);
    }

    @Override
    public <Y> SingularAttribute<? super T, Y> getVersion(Class<Y> type) {
        return (SingularAttribute<? super T, Y>) new SourcePersistentPropertySingularAttribute<>(persistentEntity.getVersion());
    }

    @Override
    public <Y> SingularAttribute<T, Y> getDeclaredVersion(Class<Y> type) {
        return (SingularAttribute<T, Y>) getVersion(type);
    }

    @Override
    public IdentifiableType<? super T> getSupertype() {
        throw notSupportedOperation();
    }

    @Override
    public boolean hasSingleIdAttribute() {
        return persistentEntity.hasIdentity();
    }

    @Override
    public boolean hasVersionAttribute() {
        return persistentEntity.hasVersion();
    }

    @Override
    public Set<SingularAttribute<? super T, ?>> getIdClassAttributes() {
        return Set.of();
    }

    @Override
    public Attribute<? super T, ?> getAttribute(String name) {
        return (Attribute<? super T, ?>) getDeclaredAttribute(name);
    }

    @Override
    public Set<Attribute<? super T, ?>> getAttributes() {
        return (Set<Attribute<? super T, ?>>) (Set<?>) getDeclaredAttributes();
    }

    @Override
    public Attribute<T, ?> getDeclaredAttribute(String name) {
        SourcePersistentProperty property = persistentEntity.getPropertyByName(name);
        if (property == null) {
            throw new IllegalArgumentException("Unknown attribute: " + name);
        }
        return SourceAttributeFactory.of(property);
    }

    @Override
    public Set<Attribute<T, ?>> getDeclaredAttributes() {
        Set<Attribute<T, ?>> attributes = new LinkedHashSet<>();
        for (SourcePersistentProperty property : persistentEntity.getPersistentProperties()) {
            attributes.add(SourceAttributeFactory.of(property));
        }
        return attributes;
    }

    @Override
    public SingularAttribute<? super T, ?> getSingularAttribute(String name) {
        return (SingularAttribute<? super T, ?>) getDeclaredSingularAttribute(name);
    }

    @Override
    public <Y> SingularAttribute<? super T, Y> getSingularAttribute(String name, Class<Y> type) {
        return (SingularAttribute<? super T, Y>) getDeclaredSingularAttribute(name);
    }

    @Override
    public SingularAttribute<T, ?> getDeclaredSingularAttribute(String name) {
        return (SingularAttribute<T, ?>) getDeclaredAttribute(name);
    }

    @Override
    public <Y> SingularAttribute<T, Y> getDeclaredSingularAttribute(String name, Class<Y> type) {
        return (SingularAttribute<T, Y>) getDeclaredSingularAttribute(name);
    }

    @Override
    public Set<SingularAttribute<? super T, ?>> getSingularAttributes() {
        return (Set<SingularAttribute<? super T, ?>>) (Set<?>) getDeclaredSingularAttributes();
    }

    @Override
    public Set<SingularAttribute<T, ?>> getDeclaredSingularAttributes() {
        Set<SingularAttribute<T, ?>> attributes = new LinkedHashSet<>();
        for (Attribute<T, ?> attribute : getDeclaredAttributes()) {
            if (attribute instanceof SingularAttribute<?, ?> singularAttribute) {
                attributes.add((SingularAttribute<T, ?>) singularAttribute);
            }
        }
        return attributes;
    }

    @Override
    public CollectionAttribute<? super T, ?> getCollection(String name) {
        return (CollectionAttribute<? super T, ?>) getDeclaredCollection(name);
    }

    @Override
    public <E> CollectionAttribute<? super T, E> getCollection(String name, Class<E> elementType) {
        return (CollectionAttribute<? super T, E>) getDeclaredCollection(name);
    }

    @Override
    public CollectionAttribute<T, ?> getDeclaredCollection(String name) {
        return (CollectionAttribute<T, ?>) getDeclaredAttribute(name);
    }

    @Override
    public <E> CollectionAttribute<T, E> getDeclaredCollection(String name, Class<E> elementType) {
        return (CollectionAttribute<T, E>) getDeclaredCollection(name);
    }

    @Override
    public SetAttribute<? super T, ?> getSet(String name) {
        return (SetAttribute<? super T, ?>) getDeclaredSet(name);
    }

    @Override
    public <E> SetAttribute<? super T, E> getSet(String name, Class<E> elementType) {
        return (SetAttribute<? super T, E>) getDeclaredSet(name);
    }

    @Override
    public SetAttribute<T, ?> getDeclaredSet(String name) {
        return (SetAttribute<T, ?>) getDeclaredAttribute(name);
    }

    @Override
    public <E> SetAttribute<T, E> getDeclaredSet(String name, Class<E> elementType) {
        return (SetAttribute<T, E>) getDeclaredSet(name);
    }

    @Override
    public ListAttribute<? super T, ?> getList(String name) {
        return (ListAttribute<? super T, ?>) getDeclaredList(name);
    }

    @Override
    public <E> ListAttribute<? super T, E> getList(String name, Class<E> elementType) {
        return (ListAttribute<? super T, E>) getDeclaredList(name);
    }

    @Override
    public ListAttribute<T, ?> getDeclaredList(String name) {
        return (ListAttribute<T, ?>) getDeclaredAttribute(name);
    }

    @Override
    public <E> ListAttribute<T, E> getDeclaredList(String name, Class<E> elementType) {
        return (ListAttribute<T, E>) getDeclaredList(name);
    }

    @Override
    public MapAttribute<? super T, ?, ?> getMap(String name) {
        return (MapAttribute<? super T, ?, ?>) getDeclaredMap(name);
    }

    @Override
    public <K, V> MapAttribute<? super T, K, V> getMap(String name, Class<K> keyType, Class<V> valueType) {
        return (MapAttribute<? super T, K, V>) getDeclaredMap(name);
    }

    @Override
    public MapAttribute<T, ?, ?> getDeclaredMap(String name) {
        return (MapAttribute<T, ?, ?>) getDeclaredAttribute(name);
    }

    @Override
    public <K, V> MapAttribute<T, K, V> getDeclaredMap(String name, Class<K> keyType, Class<V> valueType) {
        return (MapAttribute<T, K, V>) getDeclaredMap(name);
    }

    @Override
    public Set<PluralAttribute<? super T, ?, ?>> getPluralAttributes() {
        return (Set<PluralAttribute<? super T, ?, ?>>) (Set<?>) getDeclaredPluralAttributes();
    }

    @Override
    public Set<PluralAttribute<T, ?, ?>> getDeclaredPluralAttributes() {
        Set<PluralAttribute<T, ?, ?>> attributes = new LinkedHashSet<>();
        for (Attribute<T, ?> attribute : getDeclaredAttributes()) {
            if (attribute instanceof PluralAttribute<?, ?, ?> pluralAttribute) {
                attributes.add((PluralAttribute<T, ?, ?>) pluralAttribute);
            }
        }
        return attributes;
    }

    private IllegalStateException notSupportedOperation() {
        return new IllegalStateException("Not supported operation!");
    }

    private static final class SourceAttributeFactory {

        private SourceAttributeFactory() {
        }

        private static <T> Attribute<T, ?> of(SourcePersistentProperty property) {
            return new SourcePersistentPropertySingularAttribute<>(property);
        }
    }

    private static final class SourcePersistentPropertySingularAttribute<T> implements SingularAttribute<T, Object> {

        private final SourcePersistentProperty persistentProperty;

        private SourcePersistentPropertySingularAttribute(SourcePersistentProperty persistentProperty) {
            this.persistentProperty = persistentProperty;
        }

        @Override
        public boolean isId() {
            return persistentProperty == persistentProperty.getOwner().getIdentity();
        }

        @Override
        public boolean isVersion() {
            return persistentProperty.getOwner().hasVersion() && persistentProperty == persistentProperty.getOwner().getVersion();
        }

        @Override
        public boolean isOptional() {
            return persistentProperty.isOptional();
        }

        @Override
        public Type<Object> getType() {
            throw new IllegalStateException("Not supported operation!");
        }

        @Override
        public BindableType getBindableType() {
            return BindableType.SINGULAR_ATTRIBUTE;
        }

        @Override
        public Class<Object> getBindableJavaType() {
            throw new IllegalStateException("Not supported operation!");
        }

        @Override
        public String getName() {
            return persistentProperty.getName();
        }

        @Override
        public PersistentAttributeType getPersistentAttributeType() {
            return PersistentAttributeType.BASIC;
        }

        @Override
        public jakarta.persistence.metamodel.ManagedType<T> getDeclaringType() {
            throw new IllegalStateException("Not supported operation!");
        }

        @Override
        public Class<Object> getJavaType() {
            throw new IllegalStateException("Not supported operation!");
        }

        @Override
        public Member getJavaMember() {
            throw new IllegalStateException("Not supported operation!");
        }

        @Override
        public boolean isAssociation() {
            return false;
        }

        @Override
        public boolean isCollection() {
            return false;
        }
    }
}
