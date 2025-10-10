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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.intercept.ProcedureReturningOneInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.List;
import java.util.Optional;

/**
 * The default implementation of {@link ProcedureReturningOneInterceptor}.
 *
 * @param <T> The declaring type
 * @param <R> The return generic type
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public final class DefaultProcedureReturningOneInterceptor<T, R> extends AbstractQueryInterceptor<T, R> implements ProcedureReturningOneInterceptor<T, R> {

    /**
     * Default constructor.
     *
     * @param datastore The operations
     */
    DefaultProcedureReturningOneInterceptor(RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public R intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, R> context) {
        PreparedQuery<?, R> preparedQuery = prepareQuery(methodKey, context);
        List<R> results = operations.execute(preparedQuery);
        ReturnType<R> returnType = context.getReturnType();
        if (returnType.isVoid()) {
            return null;
        }
        if (returnType.isOptional()) {
            if (results.isEmpty()) {
                return (R) Optional.empty();
            }
            return (R) Optional.ofNullable(
                convertOne(context, results.get(0))
            );
        }
        if (results.isEmpty()) {
            return null;
        }
        return (R) convertOne(context, results.get(0));
    }
}
