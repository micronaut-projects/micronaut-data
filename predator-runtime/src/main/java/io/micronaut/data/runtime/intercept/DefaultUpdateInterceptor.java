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
import io.micronaut.data.intercept.UpdateInterceptor;
import io.micronaut.data.runtime.datastore.Datastore;

/**
 * Default implementation of {@link UpdateInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultUpdateInterceptor<T> extends AbstractQueryInterceptor<T, Void> implements UpdateInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The datastore
     */
    public DefaultUpdateInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Void intercept(MethodInvocationContext<T, Void> context) {
        PreparedQuery preparedQuery = prepareQuery(context);
        datastore.executeUpdate(
                preparedQuery.getQuery(),
                preparedQuery.getParameterValues()
        );
        return null;
    }
}
