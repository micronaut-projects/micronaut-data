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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.FindOptionalInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.operations.RepositoryOperations;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.model.runtime.PreparedQuery;

import java.util.Optional;

/**
 * Default implementation of {@link FindOptionalInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultFindOptionalInterceptor<T> extends AbstractQueryInterceptor<T, Optional<Object>> implements FindOptionalInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    public DefaultFindOptionalInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Optional<Object> intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Optional<Object>> context) {
        PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context);
        Object result = operations.findOne(preparedQuery);
        return Optional.ofNullable(result);
    }
}
