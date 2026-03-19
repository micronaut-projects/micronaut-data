/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.PersistentEntityMapJoin;
import io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.MapAttribute;

import java.util.List;
import java.util.Map;

@Internal
final class RuntimePersistentMapAttributePath<Owner, K, E> extends RuntimePersistentAssociationPath<Owner, E>
    implements PersistentEntityMapJoin<Owner, K, E> {

    RuntimePersistentMapAttributePath(PersistentEntityFrom<?, Owner> parent,
                                      RuntimeAssociation<Owner> association,
                                      List<Association> associations,
                                      Join.@Nullable Type associationJoinType,
                                      @Nullable String alias,
                                      CriteriaBuilder criteriaBuilder) {
        super(parent, association, associations, associationJoinType, alias, criteriaBuilder);
    }

    @Override
    public MapAttribute<? super Owner, K, E> getModel() {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public Path<K> key() {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public Path<E> value() {
        return this;
    }

    @Override
    public Expression<Map.Entry<K, E>> entry() {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public RuntimePersistentMapAttributePath<Owner, K, E> on(Expression<Boolean> restriction) {
        throw CriteriaUtils.notSupportedOperation();
    }

    @Override
    public RuntimePersistentMapAttributePath<Owner, K, E> on(Predicate... restrictions) {
        throw CriteriaUtils.notSupportedOperation();
    }
}
