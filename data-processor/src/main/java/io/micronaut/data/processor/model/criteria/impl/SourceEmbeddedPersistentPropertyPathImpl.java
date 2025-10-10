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
package io.micronaut.data.processor.model.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.DefaultEmbeddedPersistentPropertyPath;
import io.micronaut.data.processor.model.SourceAssociation;
import jakarta.persistence.criteria.Path;

import java.util.List;
import java.util.function.BiFunction;

/**
 * The internal implementation of {@link SourcePersistentPropertyPath} for embedded property.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 4.13
 */
@Internal
final class SourceEmbeddedPersistentPropertyPathImpl<T> extends DefaultEmbeddedPersistentPropertyPath<T> implements SourcePersistentPropertyPath<T> {

    private final Path<?> parentPath;
    private final SourceAssociation association;

    public SourceEmbeddedPersistentPropertyPathImpl(
        Path<?> parentPath,
        List<Association> path,
        SourceAssociation persistentProperty,
        BiFunction<Path<?>, PersistentProperty, PersistentPropertyPath<?>> getPropertyFn) {
        super(persistentProperty, path, getPropertyFn);
        this.parentPath = parentPath;
        this.association = persistentProperty;
    }

    @Override
    public Path<?> getParentPath() {
        return parentPath;
    }

    @Override
    public SourceAssociation getProperty() {
        return association;
    }

    @Override
    public String toString() {
        return "SourceEmbeddedPersistentPropertyPathImpl{" +
                "association=" + association +
                '}';
    }
}
