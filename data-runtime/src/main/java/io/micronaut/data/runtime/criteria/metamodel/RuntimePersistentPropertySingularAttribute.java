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
package io.micronaut.data.runtime.criteria.metamodel;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

@Internal
final class RuntimePersistentPropertySingularAttribute<T> extends RuntimePersistentPropertyAttribute<T, Object> implements SingularAttribute<T, Object> {

    private final RuntimePersistentEntity<T> persistentEntity;

    public RuntimePersistentPropertySingularAttribute(RuntimePersistentEntity<T> persistentEntity,
                                                      RuntimePersistentProperty<T> persistentProperty) {
        super(persistentProperty);
        this.persistentEntity = persistentEntity;
    }

    @Override
    public boolean isId() {
        return persistentEntity.getIdentity() == persistentProperty;
    }

    @Override
    public boolean isVersion() {
        return persistentEntity.getVersion() == persistentProperty;
    }

    @Override
    public boolean isOptional() {
        return persistentProperty.isOptional();
    }

    @Override
    public Type<Object> getType() {
        throw notSupportedOperation();
    }

    @Override
    public BindableType getBindableType() {
        return BindableType.SINGULAR_ATTRIBUTE;
    }

    @Override
    public Class<Object> getBindableJavaType() {
        return (Class<Object>) persistentProperty.getType();
    }

}
