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
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.intercept.async.ExistsByAsyncInterceptor;
import io.micronaut.data.model.runtime.PreparedQuery;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * The default implementation of {@link ExistsByAsyncInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultExistsByAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Boolean> implements ExistsByAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultExistsByAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Boolean> intercept(MethodInvocationContext<T, CompletionStage<Boolean>> context) {
        Class idType = context.classValue(PredatorMethod.class, PredatorMethod.META_MEMBER_ID_TYPE)
                .orElseGet(() -> getRequiredRootEntity(context));
        PreparedQuery<?, ?> preparedQuery = prepareQuery(context, idType);
        return asyncDatastoreOperations.findOptional(preparedQuery)
                .thenApply(Objects::nonNull);
    }
}
