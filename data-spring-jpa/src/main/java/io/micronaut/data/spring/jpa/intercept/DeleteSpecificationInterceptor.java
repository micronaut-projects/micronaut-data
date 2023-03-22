/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.spring.jpa.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.operations.RepositoryOperations;
import org.springframework.data.jpa.domain.Specification;

/**
 * Interceptor that supports Spring Data JPA specifications.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Internal
public class DeleteSpecificationInterceptor extends io.micronaut.data.jpa.repository.intercept.DeleteSpecificationInterceptor {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DeleteSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    protected io.micronaut.data.jpa.repository.criteria.Specification getSpecification(MethodInvocationContext<?, ?> context) {
        final Object parameterValue = context.getParameterValues()[0];
        if (parameterValue instanceof Specification springSpecification) {
            return springSpecification::toPredicate;
        }
        return super.getSpecification(context);
    }

}
