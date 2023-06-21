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
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Criteria query specification.
 *
 * Based on Spring Data's org.springframework.data.jpa.domain.Specification.
 *
 * @param <T> The entity root type
 * @author Denis Stepanov
 * @since 3.2
 */
public interface QuerySpecification<T> {

    /**
     * Include all specification.
     */
    QuerySpecification<?> ALL = (root, query, criteriaBuilder) -> null;

    /**
     * Negates the given {@link QuerySpecification}.
     *
     * @param <T>  the type of the {@link jakarta.persistence.criteria.Root} the resulting {@literal Specification} operates on.
     * @param spec The specification.
     * @return negated specification}.
     */
    @NonNull
    static <T> QuerySpecification<T> not(@Nullable QuerySpecification<T> spec) {
        if (spec == null) {
            return (QuerySpecification<T>) ALL;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.not(spec.toPredicate(root, query, criteriaBuilder));
    }

    /**
     * Simple static factory method to add some syntactic sugar around a {@link QuerySpecification}.
     *
     * @param <T>  the type of the {@link jakarta.persistence.criteria.Root} the resulting {@literal Specification} operates on.
     * @param spec The specification.
     * @return guaranteed to be not {@literal null}.
     */
    @NonNull
    static <T> QuerySpecification<T> where(@Nullable QuerySpecification<T> spec) {
        if (spec == null) {
            return (QuerySpecification<T>) ALL;
        }
        return spec;
    }

    /**
     * Simple static factory method to add some syntactic sugar around a {@link PredicateSpecification}.
     *
     * @param <T>  the type of the {@link jakarta.persistence.criteria.Root} the resulting {@literal Specification} operates on.
     * @param spec The specification.
     * @return query specification.
     */
    @NonNull
    static <T> QuerySpecification<T> where(@Nullable PredicateSpecification<T> spec) {
        if (spec == null) {
            return (QuerySpecification<T>) ALL;
        }
        return (root, query, criteriaBuilder) -> spec.toPredicate(root, criteriaBuilder);
    }

    /**
     * ANDs the given {@link QuerySpecification} to the current one.
     *
     * @param other The other predicate.
     * @return The conjunction of the specifications
     */
    @NonNull
    default QuerySpecification<T> and(@Nullable QuerySpecification<T> other) {
        return SpecificationComposition.composed(this, other, CriteriaBuilder::and);
    }

    /**
     * ORs the given specification to the current one.
     *
     * @param other The other predicate.
     * @return The disjunction of the specifications
     */
    @NonNull
    default QuerySpecification<T> or(@Nullable QuerySpecification<T> other) {
        return SpecificationComposition.composed(this, other, CriteriaBuilder::or);
    }

    /**
     * ANDs the given {@link PredicateSpecification} to the current one.
     *
     * @param other The other predicate.
     * @return The conjunction of the specifications
     */
    @NonNull
    default QuerySpecification<T> and(@Nullable PredicateSpecification<T> other) {
        return SpecificationComposition.composed(this, other, CriteriaBuilder::and);
    }

    /**
     * ORs the given {@link PredicateSpecification} to the current one.
     *
     * @param other The other predicate.
     * @return The disjunction of the specifications
     */
    @NonNull
    default QuerySpecification<T> or(@Nullable PredicateSpecification<T> other) {
        return SpecificationComposition.composed(this, other, CriteriaBuilder::or);
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
                          @NonNull CriteriaQuery<?> query,
                          @NonNull CriteriaBuilder criteriaBuilder);

}
