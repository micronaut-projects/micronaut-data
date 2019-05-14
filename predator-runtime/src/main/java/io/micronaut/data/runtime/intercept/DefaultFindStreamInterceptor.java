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
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.FindStreamInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.runtime.datastore.Datastore;

import java.util.stream.Stream;

public class DefaultFindStreamInterceptor<T> extends AbstractQueryInterceptor<T, Stream<Object>> implements FindStreamInterceptor<T> {

    public DefaultFindStreamInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Stream<Object> intercept(MethodInvocationContext<T, Stream<Object>> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery preparedQuery = prepareQuery(context);
            return datastore.findStream(
                    (Class<Object>) preparedQuery.getResultType(),
                    preparedQuery.getQuery(),
                    preparedQuery.getParameterValues(),
                    preparedQuery.getPageable()
            );
        } else {
            Class rootEntity = getRequiredRootEntity(context);
            Pageable pageable = getPageable(context);

            if (pageable != null) {
                return datastore.findStream(rootEntity, pageable);
            } else {
                return datastore.findStream(rootEntity);
            }
        }
    }
}
