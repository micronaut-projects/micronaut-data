/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.intercept.async.UpdateAsyncInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link UpdateAsyncInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultUpdateAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Number> implements UpdateAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultUpdateAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Number> intercept(MethodInvocationContext<T, CompletionStage<Number>> context) {
        PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(context);
        return asyncDatastoreOperations.executeUpdate(preparedQuery)
                .thenApply(n -> convertNumberArgumentIfNecessary(n, context.getReturnType().asArgument()));
    }
}
