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
import io.micronaut.data.intercept.FindOptionalInterceptor;
import io.micronaut.data.runtime.datastore.Datastore;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

public class DefaultFindOptionalInterceptor<T> extends AbstractQueryInterceptor<T, Optional<Object>> implements FindOptionalInterceptor<T> {

    public DefaultFindOptionalInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Optional<Object> intercept(MethodInvocationContext<T, Optional<Object>> context) {
        PreparedQuery preparedQuery = prepareQuery(context);
        return Optional.ofNullable(datastore.findOne(
                preparedQuery.getResultType(),
                preparedQuery.getQuery(),
                preparedQuery.getParameterValues()
        ));
    }
}
