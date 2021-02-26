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
package io.micronaut.data.runtime.event;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;

/**
 * Default implementation of the {@link EntityEventContext} interface.
 *
 * @param <T> The entity type
 * @author graemerocher
 * @since 2.3.0
 */
@Internal
public class DefaultEntityEventContext<T> implements EntityEventContext<T> {

    private final RuntimePersistentEntity<T> persistentEntity;
    private T entity;

    public DefaultEntityEventContext(
            RuntimePersistentEntity<T> persistentEntity,
            T entity) {
        this.persistentEntity = persistentEntity;
        this.entity = entity;
    }

    @NonNull
    @Override
    public T getEntity() {
        return entity;
    }

    @Override
    public <P> void setProperty(BeanProperty<T, P> property, P newValue) {
        if (property.hasSetterOrConstructorArgument()) {
            if (property.isReadOnly()) {
                this.entity = property.withValue(entity, newValue);
            } else {
                property.set(entity, newValue);
            }
        }
    }

    @Override
    public RuntimePersistentEntity<T> getPersistentEntity() {
        return persistentEntity;
    }
}
