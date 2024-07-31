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
import io.micronaut.data.model.jpa.criteria.impl.DefaultPersistentPropertyPath;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;

import java.util.List;

/**
 * The internal implementation of {@link SourcePersistentPropertyPath}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
final class SourcePersistentPropertyPathImpl<T> extends DefaultPersistentPropertyPath<T> implements SourcePersistentPropertyPath<T> {

    private final Path<?> parentPath;
    private final SourcePersistentProperty sourcePersistentProperty;

    public SourcePersistentPropertyPathImpl(Path<?> parentPath, List<Association> path, SourcePersistentProperty persistentProperty, CriteriaBuilder criteriaBuilder) {
        super(persistentProperty, path, criteriaBuilder);
        this.parentPath = parentPath;
        this.sourcePersistentProperty = persistentProperty;
    }

    @Override
    public Path<?> getParentPath() {
        return parentPath;
    }

    @Override
    public SourcePersistentProperty getProperty() {
        return sourcePersistentProperty;
    }

    @Override
    public String toString() {
        return "SourcePersistentPropertyPathImpl{" +
                "sourcePersistentProperty=" + sourcePersistentProperty +
                '}';
    }
}
