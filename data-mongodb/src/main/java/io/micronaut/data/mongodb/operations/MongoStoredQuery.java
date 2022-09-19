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
package io.micronaut.data.mongodb.operations;

import io.micronaut.aop.InvocationContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.operations.internal.query.PersistentEntityAwareQuery;

/**
 * MongoDB's {@link StoredQuery}.
 *
 * @param <E>   The entity type
 * @param <R>   The result type
 * @param <Dtb> The database type
 * @author Denis Stepanov
 * @since 3.3.
 */
@Experimental
public interface MongoStoredQuery<E, R, Dtb> extends StoredQuery<E, R>, PersistentEntityAwareQuery<E> {

    /**
     * @return The associated databae
     */
    Dtb getDatabase();

    /**
     * @return Is aggregation query?
     */
    boolean isAggregate();

    /**
     * @param invocationContext The invocation context to have query parameters extracted from
     * @return The data to execute the aggregation
     */
    MongoAggregation getAggregation(InvocationContext<?, ?> invocationContext);

    /**
     * @param invocationContext The invocation context to have query parameters extracted from
     * @return The data to execute the find
     */
    MongoFind getFind(InvocationContext<?, ?> invocationContext);

    /**
     * @param invocationContext The invocation context to have query parameters extracted from
     * @return The data to execute the update
     */
    MongoUpdate getUpdateMany(InvocationContext<?, ?> invocationContext);

    /**
     * @param entity The entity to have query parameters extracted from
     * @return The data to execute the update
     */
    MongoUpdate getUpdateOne(E entity);

    /**
     * @param invocationContext The invocation context to have query parameters extracted from
     * @return The data to execute the delete
     */
    MongoDelete getDeleteMany(InvocationContext<?, ?> invocationContext);

    /**
     * @param entity The entity to have query parameters extracted from
     * @return The data to execute the delete
     */
    MongoDelete getDeleteOne(E entity);

}
