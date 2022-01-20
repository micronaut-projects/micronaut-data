/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.query;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.query.internal.DefaultStoredQuery;

/**
 * Default stored query resolver.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public abstract class DefaultStoredQueryResolver implements StoredQueryResolver {

    @Override
    public <E, R> StoredQuery<E, R> resolveQuery(MethodInvocationContext<?, ?> context, Class<E> entityClass, Class<R> resultType) {
        if (resultType == null) {
            //noinspection unchecked
            resultType = (Class<R>) context.classValue(DataMethod.NAME, DataMethod.META_MEMBER_RESULT_TYPE)
                    .orElse(entityClass);
        }
        String query = context.stringValue(Query.class).orElseThrow(() ->
                new IllegalStateException("No query present in method")
        );
        return new DefaultStoredQuery<>(
                context.getExecutableMethod(),
                resultType,
                entityClass,
                query,
                false,
                getOperations()
        );
    }

    @Override
    public <E, R> StoredQuery<E, R> resolveCountQuery(MethodInvocationContext<?, ?> context, Class<E> entityClass, Class<R> resultType) {
        String query = context.stringValue(Query.class, DataMethod.META_MEMBER_COUNT_QUERY).orElseThrow(() ->
                new IllegalStateException("No query present in method")
        );
        return new DefaultStoredQuery<>(
                context.getExecutableMethod(),
                resultType,
                entityClass,
                query,
                true,
                getOperations()
        );
    }

    protected abstract RepositoryOperations getOperations();

}
