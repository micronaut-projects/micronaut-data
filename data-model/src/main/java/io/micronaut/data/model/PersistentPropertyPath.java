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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
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
    private String path;

    /**
     * Default constructor.
     *
     * @param associations The associations
     * @param property     The property
     */
    public PersistentPropertyPath(List<Association> associations, @NonNull PersistentProperty property) {
        this(associations, property, null);
    }

    /**
     * Default constructor.
     *
     * @param associations The associations
     * @param property     The property
     * @param path         The path
     */
    public PersistentPropertyPath(List<Association> associations, @NonNull PersistentProperty property, @Nullable String path) {
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
    public static PersistentPropertyPath of(List<Association> associations, @NonNull PersistentProperty property) {
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
    public static PersistentPropertyPath of(List<Association> associations, @NonNull PersistentProperty property, @Nullable String path) {
        if (property instanceof Association) {
            return new PersistentAssociationPath(associations, (Association) property, path);
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
        if (!(property instanceof RuntimePersistentProperty)) {
            throw new IllegalStateException("Expected runtime property!");
        }
        return setProperty(associations, (RuntimePersistentProperty) property, bean, value);
    }

    private Object setProperty(List<Association> associations, RuntimePersistentProperty property, Object bean, Object value) {
        if (associations.isEmpty()) {
            BeanProperty beanProperty = property.getProperty();
            return setProperty(beanProperty, bean, value);
        }
        Association association = associations.iterator().next();
        RuntimePersistentProperty<?> p = (RuntimePersistentProperty) association;
        BeanProperty beanProperty = p.getProperty();
        Object prevBean = beanProperty.get(bean);
        Object newBean = setProperty(associations.subList(1, associations.size()), property, prevBean, value);
        if (prevBean != newBean) {
            return setProperty(beanProperty, bean, newBean);
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
    public Object getPropertyValue(Object bean) {
        if (!(property instanceof RuntimePersistentProperty)) {
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
        RuntimePersistentProperty<Object> p = (RuntimePersistentProperty<Object>) property;
        if (value != null) {
            BeanProperty<Object, Object> beanProperty = p.getProperty();
            value = beanProperty.get(value);
        }
        return value;
    }

    /**
     * @return The associations
     */
    @NonNull
    public List<Association> getAssociations() {
        return associations;
    }

    /**
     * @return The property
     */
    @NonNull
    public PersistentProperty getProperty() {
        return property;
    }

    /**
     * @return The path
     */
    @NonNull
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
     * @return The array path
     */
    @NonNull
    public String[] getArrayPath() {
        if (path == null) {
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
        return new String[0];
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
}
