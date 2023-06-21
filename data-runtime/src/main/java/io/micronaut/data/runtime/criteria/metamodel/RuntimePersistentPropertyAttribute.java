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
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;

import java.lang.reflect.Member;

@Internal
abstract class RuntimePersistentPropertyAttribute<T, E> implements Attribute<T, E> {

    protected final RuntimePersistentProperty<T> persistentProperty;

    public RuntimePersistentPropertyAttribute(RuntimePersistentProperty<T> persistentProperty) {
        this.persistentProperty = persistentProperty;
    }

    @Override
    public String getName() {
        return persistentProperty.getName();
    }

    @Override
    public PersistentAttributeType getPersistentAttributeType() {
        if (persistentProperty instanceof RuntimeAssociation runtimeAssociation) {
            return switch (runtimeAssociation.getKind()) {
                case EMBEDDED -> PersistentAttributeType.EMBEDDED;
                case ONE_TO_ONE -> PersistentAttributeType.ONE_TO_ONE;
                case MANY_TO_ONE -> PersistentAttributeType.MANY_TO_ONE;
                case ONE_TO_MANY -> PersistentAttributeType.ONE_TO_MANY;
                case MANY_TO_MANY -> PersistentAttributeType.MANY_TO_MANY;
                default -> PersistentAttributeType.BASIC;
            };
        }
        return PersistentAttributeType.BASIC;
    }

    @Override
    public ManagedType<T> getDeclaringType() {
        throw notSupportedOperation();
    }

    @Override
    public Class<E> getJavaType() {
        return (Class<E>) persistentProperty.getType();
    }

    @Override
    public Member getJavaMember() {
        throw notSupportedOperation();
    }

    @Override
    public boolean isAssociation() {
        return persistentProperty instanceof RuntimeAssociation;
    }

    @Override
    public boolean isCollection() {
        return false;
    }

    protected IllegalStateException notSupportedOperation() {
        return new IllegalStateException("Not supported operation!");
    }
}
