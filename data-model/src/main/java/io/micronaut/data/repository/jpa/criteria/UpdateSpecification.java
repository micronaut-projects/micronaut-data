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
package io.micronaut.data.repository.jpa.criteria;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Criteria update specification.
 *
 * @param <T> The entity root type
 * @author Denis Stepanov
 * @since 3.2
 */
public interface UpdateSpecification<T> {

    /**
     * Simple static factory method to add some syntactic sugar around a {@link UpdateSpecification}.
     *
     * @param spec The predicate specification.
     * @return query specification.
     */
    @NonNull
    default UpdateSpecification<T> where(@Nullable PredicateSpecification<T> spec) {
        if (spec == null) {
            return this;
        }
        return SpecificationComposition.composed(this, spec, CriteriaBuilder::and);
    }

    /**
     * Creates a WHERE clause predicate for the given entity {@link Root} and a criteria query.
     *
     * @param root            The entity root
     * @param query           The criteria query
     * @param criteriaBuilder The criteria builder
     * @return a {@link Predicate}
     */
    @Nullable
    Predicate toPredicate(@NonNull Root<T> root,
                          @NonNull CriteriaUpdate<?> query,
                          @NonNull CriteriaBuilder criteriaBuilder);

}
