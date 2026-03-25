/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.model;

import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * The property path representation.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
public class PersistentPropertyPath {
    private final List<Association> associations;
    private final PersistentProperty property;
    @Nullable
    private String path;

    /**
     * Default constructor.
     *
     * @param property     The property
     */
    public PersistentPropertyPath(PersistentProperty property) {
        this(List.of(), property, null);
    }

    /**
     * Default constructor.
     *
     * @param associations The associations
     * @param property     The property
     */
    public PersistentPropertyPath(List<Association> associations,  PersistentProperty property) {
        this(associations, property, null);
    }

    /**
     * Default constructor.
     *
     * @param associations The associations
     * @param property     The property
     * @param path         The path
     */
    public PersistentPropertyPath(List<Association> associations,  PersistentProperty property, @Nullable String path) {
        Objects.requireNonNull(associations);
        Objects.requireNonNull(property);
        this.associations = associations;
        this.property = property;
        this.path = path;
    }

    /**
     * Creates {@link PersistentPropertyPath} or {@link PersistentAssociationPath}.
     *
     * @param associations The associations
     * @param property     The property
     * @return new instance of {@link PersistentPropertyPath} or {@link PersistentAssociationPath}
     */
    public static PersistentPropertyPath of(List<Association> associations,  PersistentProperty property) {
        return of(associations, property, null);
    }

    /**
     * Creates {@link PersistentPropertyPath} or {@link PersistentAssociationPath}.
     *
     * @param associations The associations
     * @param property     The property
     * @param path         The path
     * @return new instance of {@link PersistentPropertyPath} or {@link PersistentAssociationPath}
     */
    public static PersistentPropertyPath of(List<Association> associations,  PersistentProperty property, @Nullable String path) {
        if (property instanceof Association association) {
            return new PersistentAssociationPath(associations, association, path);
        }
        return new PersistentPropertyPath(associations, property, path);
    }

    /**
     * Sets property path value.
     * (Only possible for runtime properties)
     *
     * @param bean  The root bean
     * @param value The value
     * @return The root bean - possibly modified
     */
    public Object setPropertyValue(Object bean, Object value) {
        if (!(property instanceof RuntimePersistentProperty<?> runtimeProperty)) {
            throw new IllegalStateException("Expected runtime property!");
        }
        return setProperty(associations, runtimeProperty, bean, value);
    }

    private Object setProperty(List<Association> associations, RuntimePersistentProperty<?> property, Object bean, Object value) {
        if (associations.isEmpty()) {
            BeanProperty beanProperty = property.getProperty();
            return setProperty(beanProperty, bean, value);
        }
        Association association = associations.iterator().next();
        RuntimePersistentProperty<?> p = (RuntimePersistentProperty) association;
        BeanProperty beanProperty = p.getProperty();
        Object prevBean = beanProperty.get(bean);
        if (prevBean != null) {
            Object newBean = setProperty(associations.subList(1, associations.size()), property, prevBean, value);
            if (prevBean != newBean) {
                return setProperty(beanProperty, bean, newBean);
            }
        }
        return bean;
    }

    private <X, Y> X setProperty(BeanProperty<X, Y> beanProperty, X x, Y y) {
        if (beanProperty.isReadOnly()) {
            return beanProperty.withValue(x, y);
        }
        beanProperty.set(x, y);
        return x;
    }

    /**
     * Gets property path value.
     * (Only possible for runtime properties)
     *
     * @param bean The root bean
     * @return The value
     */
    @Nullable
    public Object getPropertyValue(Object bean) {
        if (!(property instanceof RuntimePersistentProperty p)) {
            throw new IllegalStateException("Expected runtime property!");
        }
        Object value = bean;
        for (Association association : associations) {
            RuntimePersistentProperty<Object> property = (RuntimePersistentProperty<Object>) association;
            BeanProperty<Object, Object> beanProperty = property.getProperty();
            value = beanProperty.get(value);
            if (value == null) {
                return null;
            }
        }
        BeanProperty<Object, Object> beanProperty = p.getProperty();
        return beanProperty.get(value);
    }

    /**
     * @return The associations
     */
    public List<Association> getAssociations() {
        return associations;
    }

    /**
     * @return The property
     */
    public PersistentProperty getProperty() {
        return property;
    }

    /**
     * @return The path
     */
    public String getPath() {
        if (path == null) {
            if (associations.isEmpty()) {
                return property.getName();
            }
            StringJoiner joiner = new StringJoiner(".");
            for (Association association : associations) {
                joiner.add(association.getName());
            }
            joiner.add(property.getName());
            path = joiner.toString();
        }
        return path;
    }

    /**
     * @return The associations path
     */
    public String getAssociationsPath() {
        if (associations.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(".");
        for (Association association : associations) {
            joiner.add(association.getName());
        }
        return joiner.toString();
    }

    /**
     * @return The array path
     */
    public String[] getArrayPath() {
        if (associations.isEmpty()) {
            return new String[]{property.getName()};
        }
        List<String> strings = new ArrayList<>(associations.size() + 1);
        for (Association association : associations) {
            strings.add(association.getName());
        }
        strings.add(property.getName());
        return strings.toArray(new String[0]);
    }

    /**
     * @return The array path
     */
    public List<PersistentProperty> getPropertyPath() {
        if (associations.isEmpty()) {
            return List.of(property);
        }
        List<PersistentProperty> props = new ArrayList<>(associations.size() + 1);
        props.addAll(associations);
        props.add(property);
        return props;
    }

    /**
     * Find the owner of the possible embedded property.
     *
     * @return the optional owner
     */
    public Optional<PersistentEntity> findPropertyOwner() {
        PersistentEntity owner = property.getOwner();
        if (!owner.isEmbeddable()) {
            return Optional.of(owner);
        }
        ListIterator<Association> listIterator = associations.listIterator(associations.size());
        while (listIterator.hasPrevious()) {
            Association association = listIterator.previous();
            if (!association.getOwner().isEmbeddable()) {
                return Optional.of(association.getOwner());
            }
        }
        return Optional.empty();
    }

    /**
     * Get naming strategy for the property.
     *
     * @return the naming strategy
     */
    public NamingStrategy getNamingStrategy() {
        PersistentEntity owner = property.getOwner();
        if (!owner.isEmbeddable()) {
            return owner.getNamingStrategy();
        }
        Optional<NamingStrategy> namingStrategy = owner.findNamingStrategy();
        if (namingStrategy.isPresent()) {
            return namingStrategy.get();
        }
        ListIterator<Association> listIterator = associations.listIterator(associations.size());
        while (listIterator.hasPrevious()) {
            Association association = listIterator.previous();
            if (!association.getOwner().isEmbeddable()) {
                return association.getOwner().getNamingStrategy();
            }
            Optional<NamingStrategy> embeddedNamingStrategy = owner.findNamingStrategy();
            if (embeddedNamingStrategy.isPresent()) {
                return embeddedNamingStrategy.get();
            }
        }
        return owner.getNamingStrategy();
    }

    /**
     * Finds naming strategy for the property.
     *
     * @return the naming strategy
     */
    public Optional<NamingStrategy> findNamingStrategy() {
        PersistentEntity owner = property.getOwner();
        if (!owner.isEmbeddable()) {
            return owner.findNamingStrategy();
        }
        Optional<NamingStrategy> namingStrategy = owner.findNamingStrategy();
        if (namingStrategy.isPresent()) {
            return namingStrategy;
        }
        ListIterator<Association> listIterator = associations.listIterator(associations.size());
        while (listIterator.hasPrevious()) {
            Association association = listIterator.previous();
            if (!association.getOwner().isEmbeddable()) {
                return association.getOwner().findNamingStrategy();
            }
            Optional<NamingStrategy> embeddedNamingStrategy = owner.findNamingStrategy();
            if (embeddedNamingStrategy.isPresent()) {
                return embeddedNamingStrategy;
            }
        }
        return owner.findNamingStrategy();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PersistentPropertyPath that = (PersistentPropertyPath) o;
        return Objects.equals(associations, that.associations) && Objects.equals(property, that.property);
    }

    @Override
    public int hashCode() {
        return property.hashCode();
    }

    @Override
    public String toString() {
        return "PersistentPropertyPath{associations=" + associations + ", property=" + property + '}';
    }
}
