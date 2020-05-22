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
package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.intercept.SaveOneInterceptor;

import java.util.Map;

/**
 * Default implementation of {@link SaveOneInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveOneInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements SaveOneInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    protected DefaultSaveOneInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Object> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        Object instance = instantiateEntity(rootEntity, parameterValueMap);
        return operations.persist(getInsertOperation(context, instance));
    }
}
