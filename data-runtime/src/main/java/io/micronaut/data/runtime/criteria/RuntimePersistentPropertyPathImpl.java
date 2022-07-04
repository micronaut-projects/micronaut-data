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
package io.micronaut.data.runtime.criteria;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentPropertyPath;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import jakarta.persistence.criteria.Path;

import java.util.List;

/**
 * The runtime property path.
 *
 * @param <I> The entity type
 * @param <T> The property type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
final class RuntimePersistentPropertyPathImpl<I, T> extends AbstractPersistentPropertyPath<T> {

    private final Path<?> parentPath;
    private final RuntimePersistentProperty<I> runtimePersistentProperty;

    public RuntimePersistentPropertyPathImpl(Path<?> parentPath, List<Association> path, RuntimePersistentProperty<I> persistentProperty) {
        super(persistentProperty, path);
        this.parentPath = parentPath;
        this.runtimePersistentProperty = persistentProperty;
    }

    @Override
    public Path<?> getParentPath() {
        return parentPath;
    }

    @Override
    public Class<? extends T> getJavaType() {
        return (Class<? extends T>) runtimePersistentProperty.getType();
    }

    @Override
    public String toString() {
        return "RuntimePersistentPropertyPath{" +
            "runtimePersistentProperty=" + runtimePersistentProperty +
            '}';
    }
}
