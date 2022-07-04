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
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.Type;

@Internal
abstract class RuntimePersistentPropertyPluralAttribute<T, C, E> extends RuntimePersistentPropertyAttribute<T, C>
    implements PluralAttribute<T, C, E> {

    protected final RuntimePersistentEntity<T> persistentEntity;
    protected final RuntimeAssociation<T> association;

    public RuntimePersistentPropertyPluralAttribute(RuntimePersistentEntity<T> persistentEntity,
                                                    RuntimePersistentProperty<T> persistentProperty) {
        super(persistentProperty);
        this.persistentEntity = persistentEntity;
        this.association = (RuntimeAssociation<T>) persistentProperty;
    }

    @Override
    public BindableType getBindableType() {
        return BindableType.PLURAL_ATTRIBUTE;
    }

    @Override
    public Class<E> getBindableJavaType() {
        return (Class<E>) persistentProperty.getType();
    }

    @Override
    public Type<E> getElementType() {
        throw notSupportedOperation();
    }
}
