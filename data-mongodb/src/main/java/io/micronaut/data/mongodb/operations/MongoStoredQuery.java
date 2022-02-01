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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.StoredQuery;
import org.bson.BsonArray;
import org.bson.BsonDocument;

/**
 * MongoDB's {@link StoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.3.
 */
@Experimental
public interface MongoStoredQuery<E, R> extends StoredQuery<E, R> {

    /**
     * @return The filter
     */
    @Nullable
    BsonDocument getFilter();

    /**
     * @return The aggregation pipeline
     */
    @Nullable
    BsonArray getPipeline();

    /**
     * @return The update
     */
    @Nullable
    BsonDocument getUpdate();

}
