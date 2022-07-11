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
package io.micronaut.data.runtime.intercept.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.async.SaveOneAsyncInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link SaveOneAsyncInterceptor}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveOneAsyncInterceptor extends AbstractCountConvertCompletionStageInterceptor implements SaveOneAsyncInterceptor<Object> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    protected DefaultSaveOneAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    protected CompletionStage<?> interceptCompletionStage(RepositoryMethodKey methodKey, MethodInvocationContext<Object, CompletionStage<Object>> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        Map<String, Object> parameterValueMap = getParameterValueMap(context);
        Object o = instantiateEntity(rootEntity, parameterValueMap);
        return asyncDatastoreOperations.persist(getInsertOperation(context, o));
    }

}
