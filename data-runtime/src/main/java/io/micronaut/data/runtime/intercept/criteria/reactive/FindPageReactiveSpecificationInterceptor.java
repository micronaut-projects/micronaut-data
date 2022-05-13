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
package io.micronaut.data.runtime.intercept.criteria.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Runtime implementation of {@code Publisher<Page> find(Specification, Pageable)}.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public class FindPageReactiveSpecificationInterceptor extends AbstractReactiveSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindPageReactiveSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.getParameterValues().length != 2) {
            throw new IllegalStateException("Expected exactly 2 arguments to method");
        }

        Publisher<?> result;

        Pageable pageable = getPageable(context);
        if (pageable.isUnpaged()) {
            PreparedQuery<?, ?> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.FIND_PAGE);
            Flux<?> results = Flux.from(reactiveOperations.findAll(preparedQuery));
            result = results.collectList().map(resultList -> Page.of(resultList, pageable, resultList.size()));
        } else {
            PreparedQuery<?, ?> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.FIND_PAGE);
            PreparedQuery<?, Number> countQuery = preparedQueryForCriteria(methodKey, context, Type.COUNT);

            Flux<?> results = Flux.from(reactiveOperations.findAll(preparedQuery));

            Mono<Number> count = Mono.from(reactiveOperations.findOne(countQuery));

            result = results.collectList()
                    .zipWith(count)
                    .map(tuple -> Page.of(tuple.getT1(), getPageable(context), tuple.getT2().longValue()));
        }
        return Publishers.convertPublisher(result, context.getReturnType().getType());

    }

}
