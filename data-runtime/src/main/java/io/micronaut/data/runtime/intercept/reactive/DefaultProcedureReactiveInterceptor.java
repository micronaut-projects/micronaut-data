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
package io.micronaut.data.runtime.intercept.reactive;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.reactive.ProcedureReactiveInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

/**
 * The default implementation of {@link ProcedureReactiveInterceptor}.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public class DefaultProcedureReactiveInterceptor extends AbstractPublisherInterceptor implements ProcedureReactiveInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    DefaultProcedureReactiveInterceptor(RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    protected Publisher<?> interceptPublisher(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        PreparedQuery<?, Object> preparedQuery = prepareQuery(methodKey, context, null);
        return reactiveOperations.execute(preparedQuery);
    }

}
