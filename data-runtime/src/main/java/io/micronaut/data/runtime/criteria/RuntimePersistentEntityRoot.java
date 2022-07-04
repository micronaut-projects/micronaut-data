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
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.SelectionVisitor;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import jakarta.persistence.metamodel.EntityType;

import java.util.Collections;
import java.util.List;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The runtime entity root.
 *
 * @param <T> The  entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
final class RuntimePersistentEntityRoot<T> extends AbstractRuntimePersistentEntityJoinSupport<T, T>
        implements RuntimePersistentEntityPath<T>, PersistentEntityRoot<T> {

    private final RuntimePersistentEntity<T> runtimePersistentEntity;

    public RuntimePersistentEntityRoot(RuntimePersistentEntity<T> runtimePersistentEntity) {
        this.runtimePersistentEntity = runtimePersistentEntity;
    }

    @Override
    public void accept(SelectionVisitor selectionVisitor) {
        selectionVisitor.visit(this);
    }

    @Override
    public RuntimePersistentEntity<T> getPersistentEntity() {
        return runtimePersistentEntity;
    }

    @Override
    public Class<? extends T> getJavaType() {
        return runtimePersistentEntity.getIntrospection().getBeanType();
    }

    @Override
    public boolean isBoolean() {
        return false;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isComparable() {
        return false;
    }

    @Override
    public EntityType<T> getModel() {
        throw notSupportedOperation();
    }

    @Override
    protected List<Association> getCurrentPath() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "RuntimePersistentEntityRoot{" +
                "runtimePersistentEntity=" + runtimePersistentEntity +
                '}';
    }
}
