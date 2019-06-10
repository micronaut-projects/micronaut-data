/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.intercept.reactive;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.reactive.UpdateReactiveInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;
import org.reactivestreams.Publisher;

/**
 * Default implementation of {@link UpdateReactiveInterceptor}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultUpdateReactiveInterceptor extends AbstractReactiveInterceptor<Object, Object>
        implements UpdateReactiveInterceptor<Object, Object> {
    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected DefaultUpdateReactiveInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(context);
        ReturnType<Object> returnType = context.getReturnType();
        Publisher<Number> publisher = Publishers.map(reactiveOperations.executeUpdate(preparedQuery),
                n -> convertNumberArgumentIfNecessary(n, returnType.asArgument())
        );
        return Publishers.convertPublisher(publisher, returnType.getType());
    }
}
