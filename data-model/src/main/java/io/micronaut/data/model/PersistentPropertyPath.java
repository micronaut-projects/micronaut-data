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
import io.micronaut.data.model.naming.NamingStrategy;

import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

/**
 * The property path representation.
 *
 * @since 2.4.0
 * @author Denis Stepanov
 */
public class PersistentPropertyPath {
    private final List<Association> associations;
    private final PersistentProperty property;
    private final String path;

    /**
     * Default constructor.
     *  @param associations The associations
     * @param property     The property
     * @param path         The path
     */
    public PersistentPropertyPath(List<Association> associations, @NonNull PersistentProperty property, @NonNull String path) {
        this.associations = associations;
        this.property = property;
        this.path = path;
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
        return path;
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
     * Get naming strategy for thpe property.
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
}
