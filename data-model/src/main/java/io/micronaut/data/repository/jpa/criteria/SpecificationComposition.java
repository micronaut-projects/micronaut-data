/*
 * Copyright 2017-2020 original authors
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
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.io.Serializable;

/**
 * Helper class to support specification compositions.
 * <p>
 * Based on Spring Data's {@link org.springframework.data.jpa.domain.SpecificationComposition}.
 *
 * @author Sebastian Staudt
 * @author Oliver Gierke
 * @author Jens Schauder
 * @author Denis Stepanov
 * @since 3.2
 */
class SpecificationComposition {

    @NonNull
    static <T> QuerySpecification<T> composed(@Nullable QuerySpecification<T> lhs, @Nullable QuerySpecification<T> rhs, Combiner combiner) {
        return (root, query, builder) -> {
            Predicate otherPredicate = toPredicate(lhs, root, query, builder);
            Predicate thisPredicate = toPredicate(rhs, root, query, builder);

            if (thisPredicate == null) {
                return otherPredicate;
            }

            return otherPredicate == null ? thisPredicate : combiner.combine(builder, thisPredicate, otherPredicate);
        };
    }

    @NonNull
    static <T> QuerySpecification<T> composed(@Nullable QuerySpecification<T> lhs, @Nullable PredicateSpecification<T> rhs, Combiner combiner) {
        return (root, query, builder) -> {
            Predicate otherPredicate = toPredicate(lhs, root, query, builder);
            Predicate thisPredicate = toPredicate(rhs, root, builder);

            if (thisPredicate == null) {
                return otherPredicate;
            }

            return otherPredicate == null ? thisPredicate : combiner.combine(builder, thisPredicate, otherPredicate);
        };
    }

    @NonNull
    static <T> UpdateSpecification<T> composed(@Nullable UpdateSpecification<T> lhs, @Nullable PredicateSpecification<T> rhs, Combiner combiner) {
        return (root, query, builder) -> {
            Predicate otherPredicate = toPredicate(lhs, root, query, builder);
            Predicate thisPredicate = toPredicate(rhs, root, builder);

            if (thisPredicate == null) {
                return otherPredicate;
            }

            return otherPredicate == null ? thisPredicate : combiner.combine(builder, thisPredicate, otherPredicate);
        };
    }

    @NonNull
    static <T> DeleteSpecification<T> composed(@Nullable DeleteSpecification<T> lhs, @Nullable DeleteSpecification<T> rhs, Combiner combiner) {
        return (root, query, builder) -> {
            Predicate otherPredicate = toPredicate(lhs, root, query, builder);
            Predicate thisPredicate = toPredicate(rhs, root, query, builder);

            if (thisPredicate == null) {
                return otherPredicate;
            }

            return otherPredicate == null ? thisPredicate : combiner.combine(builder, thisPredicate, otherPredicate);
        };
    }

    @NonNull
    static <T> DeleteSpecification<T> composed(@Nullable DeleteSpecification<T> lhs, @Nullable PredicateSpecification<T> rhs, Combiner combiner) {
        return (root, query, builder) -> {
            Predicate otherPredicate = toPredicate(lhs, root, query, builder);
            Predicate thisPredicate = toPredicate(rhs, root, builder);

            if (thisPredicate == null) {
                return otherPredicate;
            }

            return otherPredicate == null ? thisPredicate : combiner.combine(builder, thisPredicate, otherPredicate);
        };
    }

    @NonNull
    static <T> PredicateSpecification<T> composed(@Nullable PredicateSpecification<T> lhs, @Nullable PredicateSpecification<T> rhs, Combiner combiner) {
        return (root, builder) -> {
            Predicate otherPredicate = toPredicate(lhs, root, builder);
            Predicate thisPredicate = toPredicate(rhs, root, builder);

            if (thisPredicate == null) {
                return otherPredicate;
            }

            return otherPredicate == null ? thisPredicate : combiner.combine(builder, thisPredicate, otherPredicate);
        };
    }

    @Nullable
    private static <T> Predicate toPredicate(@Nullable QuerySpecification<T> specification,
                                             @NonNull Root<T> root,
                                             @NonNull CriteriaQuery<?> query,
                                             @NonNull CriteriaBuilder builder) {
        return specification == null ? null : specification.toPredicate(root, query, builder);
    }

    @Nullable
    private static <T> Predicate toPredicate(@Nullable UpdateSpecification<T> specification,
                                             @NonNull Root<T> root,
                                             @NonNull CriteriaUpdate<?> query,
                                             @NonNull CriteriaBuilder builder) {
        return specification == null ? null : specification.toPredicate(root, query, builder);
    }

    @Nullable
    private static <T> Predicate toPredicate(@Nullable DeleteSpecification<T> specification,
                                             @NonNull Root<T> root,
                                             @NonNull CriteriaDelete<?> query,
                                             @NonNull CriteriaBuilder builder) {
        return specification == null ? null : specification.toPredicate(root, query, builder);
    }

    @Nullable
    private static <T> Predicate toPredicate(@Nullable PredicateSpecification<T> specification,
                                             @NonNull Root<T> root,
                                             @NonNull CriteriaBuilder builder) {
        return specification == null ? null : specification.toPredicate(root, builder);
    }

    interface Combiner extends Serializable {
        @NonNull
        Predicate combine(@NonNull CriteriaBuilder builder, @Nullable Predicate lhs, @Nullable Predicate rhs);
    }
}
