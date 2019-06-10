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
package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.FindByIdInterceptor;
import io.micronaut.data.operations.RepositoryOperations;

import java.io.Serializable;

/**
 * Default implementation that handles lookup by ID.
 *
 * @param <T> The declaring type.
 */
public class DefaultFindByIdInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements FindByIdInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The operations
     */
    public DefaultFindByIdInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(MethodInvocationContext<T, Object> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        Object id = context.getParameterValues()[0];
        if (!(id instanceof Serializable)) {
            throw new IllegalArgumentException("Entity IDs must be serializable!");
        }
        return operations.findOne(rootEntity, (Serializable) id);
    }
}
