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
package io.micronaut.data.hibernate.reactive.repository.jpa.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collections;

/**
 * Interceptor that supports count specifications.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public class ReactiveCountSpecificationInterceptor extends AbstractSpecificationInterceptor {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    public ReactiveCountSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    protected Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        Specification<Object> specification = getSpecification(context);
        final CriteriaBuilder criteriaBuilder = operations.getCriteriaBuilder();
        final CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
        final Root<Object> root = query.from(getRequiredRootEntity(context));
        final Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        if (query.isDistinct()) {
            query.select(criteriaBuilder.countDistinct(root));
        } else {
            query.select(criteriaBuilder.count(root));
        }
        query.orderBy(Collections.emptyList());
        return operations.withSession(session -> Mono.fromCompletionStage(() -> session.createQuery(query).getSingleResult()));
    }

}
