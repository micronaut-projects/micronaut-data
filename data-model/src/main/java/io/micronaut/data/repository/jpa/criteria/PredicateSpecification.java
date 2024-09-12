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
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * The predicate specification.
 *
 * @param <T> The entity root type
 * @author Denis Stepanov
 * @since 3.2
 */
public interface PredicateSpecification<T> {

    /**
     * Include all specification.
     */
    PredicateSpecification<?> ALL = (root, criteriaBuilder) -> null;

    /**
     * Simple static factory method to add some syntactic sugar around a {@link PredicateSpecification}.
     *
     * @param <T>  the type of the {@link Root} the resulting {@literal Specification} operates on.
     * @param spec The specification.
     * @return predicate specification.
     */
    @NonNull
    static <T> PredicateSpecification<T> where(@Nullable PredicateSpecification<T> spec) {
        if (spec == null) {
            return (PredicateSpecification<T>) ALL;
        }
        return spec;
    }

    /**
     * Negates the given {@link PredicateSpecification}.
     *
     * @param <T>  the type of the {@link Root} the resulting {@literal Specification} operates on.
     * @param spec The specification.
     * @return negated specification}.
     */
    @NonNull
    static <T> PredicateSpecification<T> not(@Nullable PredicateSpecification<T> spec) {
        if (spec == null) {
            return (PredicateSpecification<T>) ALL;
        }
        return (root, criteriaBuilder) -> criteriaBuilder.not(spec.toPredicate(root, criteriaBuilder));
    }

    /**
     * ANDs the given {@link PredicateSpecification} to the current one.
     *
     * @param other The other predicate.
     * @return The conjunction of the specifications
     */
    @NonNull
    default PredicateSpecification<T> and(@Nullable PredicateSpecification<T> other) {
        return SpecificationComposition.composed(this, other, CriteriaBuilder::and);
    }

    /**
     * ORs the given {@link PredicateSpecification} to the current one.
     *
     * @param other The other predicate.
     * @return The disjunction of the specifications
     */
    @NonNull
    default PredicateSpecification<T> or(@Nullable PredicateSpecification<T> other) {
        return SpecificationComposition.composed(this, other, CriteriaBuilder::or);
    }

    /**
     * Creates a WHERE clause predicate for the given entity {@link Root}.
     *
     * @param root            The entity root.
     * @param criteriaBuilder The criteria builder.
     * @return a {@link Predicate}
     */
    @Nullable
    Predicate toPredicate(@NonNull Root<T> root,
                          @NonNull CriteriaBuilder criteriaBuilder);

}
