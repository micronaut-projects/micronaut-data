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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.runtime.datastore.Datastore;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.runtime.datastore.PreparedQuery;

/**
 * Default implementation of {@link DeleteAllInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultDeleteAllInterceptor<T> extends AbstractQueryInterceptor<T, Void> implements DeleteAllInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The datastore
     */
    protected DefaultDeleteAllInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Void intercept(MethodInvocationContext<T, Void> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, Number> preparedQuery = (PreparedQuery<?, Number>) prepareQuery(context);
            datastore.executeUpdate(preparedQuery);
        } else {
            Object[] parameterValues = context.getParameterValues();
            Class rootEntity = getRequiredRootEntity(context);
            if (parameterValues.length == 1 && parameterValues[0] instanceof Iterable) {
                datastore.deleteAll(rootEntity, (Iterable) parameterValues[0]);
            } else if (parameterValues.length == 0) {
                datastore.deleteAll(rootEntity);
            } else {
                throw new IllegalArgumentException("Unexpected argument types received to deleteAll method");
            }
        }
        return null;
    }
}
