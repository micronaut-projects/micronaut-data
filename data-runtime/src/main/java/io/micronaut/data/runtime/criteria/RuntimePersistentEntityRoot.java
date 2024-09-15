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
import io.micronaut.data.model.jpa.criteria.ExpressionType;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCommonAbstractCriteria;
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot;
import io.micronaut.data.model.jpa.criteria.impl.expression.ClassExpressionType;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
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

    private final PersistentEntityCommonAbstractCriteria commonAbstractCriteria;
    private final RuntimePersistentEntity<T> runtimePersistentEntity;

    public RuntimePersistentEntityRoot(PersistentEntityCommonAbstractCriteria commonAbstractCriteria,
                                       RuntimePersistentEntity<T> runtimePersistentEntity,
                                       CriteriaBuilder criteriaBuilder) {
        super(criteriaBuilder);
        this.commonAbstractCriteria = commonAbstractCriteria;
        this.runtimePersistentEntity = runtimePersistentEntity;
    }

    @Override
    public Path<?> getParentPath() {
        return null;
    }

    @Override
    public ExpressionType<T> getExpressionType() {
        return new ClassExpressionType<>(runtimePersistentEntity.getIntrospection().getBeanType());
    }

    @Override
    public RuntimePersistentEntity<T> getPersistentEntity() {
        return runtimePersistentEntity;
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
