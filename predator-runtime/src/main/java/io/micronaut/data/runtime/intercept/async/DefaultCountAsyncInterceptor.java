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
package io.micronaut.data.runtime.intercept.async;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.backend.Datastore;
import io.micronaut.data.intercept.async.CountAsyncInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PreparedQuery;

import java.util.Iterator;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link CountAsyncInterceptor}.
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class DefaultCountAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Long> implements CountAsyncInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The datastore
     */
    protected DefaultCountAsyncInterceptor(Datastore datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Long> intercept(MethodInvocationContext<T, CompletionStage<Long>> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery<?, Long> preparedQuery = prepareQuery(context, Long.class);
            return asyncDatastoreOperations.findAll(preparedQuery)
                    .thenApply(longs -> {
                        long result = 0L;
                        Iterator<Long> i = longs.iterator();
                        if (i.hasNext()) {
                            result = i.next();
                        }
                        return result;
                    });
        } else {
            Class<?> rootEntity = getRequiredRootEntity(context);
            Pageable pageable = getPageable(context);


            CompletionStage<Long> result;
            if (pageable != null) {
                result = asyncDatastoreOperations.count(rootEntity, pageable);
            } else {
                result = asyncDatastoreOperations.count(rootEntity, Pageable.unpaged());
            }
            return result;
        }
    }
}
