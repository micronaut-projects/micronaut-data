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
import io.micronaut.data.intercept.ExistsByInterceptor;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.operations.RepositoryOperations;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.model.runtime.PreparedQuery;

/**
 * The default implementation of {@link ExistsByInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultExistsByInterceptor<T> extends AbstractQueryInterceptor<T, Boolean> implements ExistsByInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    protected DefaultExistsByInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Boolean intercept(RepositoryMethodKey methodKey, MethodInvocationContext<T, Boolean> context) {
        Class idType = context.classValue(DataMethod.class, DataMethod.META_MEMBER_ID_TYPE)
                .orElseGet(() -> getRequiredRootEntity(context));
        PreparedQuery<?, ?> preparedQuery = prepareQuery(methodKey, context, idType);
        return operations.exists(preparedQuery);
    }
}
