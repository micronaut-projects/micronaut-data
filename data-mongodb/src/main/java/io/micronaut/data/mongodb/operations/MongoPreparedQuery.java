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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;

/**
 * MongoDB's {@link PreparedQuery}.
 *
 * @param <E>   The entity type
 * @param <R>   The result type
 * @author Denis Stepanov
 * @since 3.3.0
 */
@Experimental
public interface MongoPreparedQuery<E, R> extends PreparedQuery<E, R> {

    /**
     * @return The persistent entity
     */
    RuntimePersistentEntity<E> getPersistentEntity();

    /**
     * @return Is aggregation query?
     */
    boolean isAggregate();

    /**
     * @return The data to execute the aggregation
     */
    MongoAggregation getAggregation();

    /**
     * @return The data to execute the find
     */
    MongoFind getFind();

    /**
     * @return The data to execute the update many
     */
    MongoUpdate getUpdateMany();

    /**
     * @return The data to execute the delete many
     */
    MongoDelete getDeleteMany();

}
