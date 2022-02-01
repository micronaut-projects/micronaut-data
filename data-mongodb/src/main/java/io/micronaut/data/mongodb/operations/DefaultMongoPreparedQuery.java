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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import org.bson.BsonArray;
import org.bson.BsonDocument;

/**
 * Default implementation of {@link MongoPreparedQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.3.
 */
@Internal
final class DefaultMongoPreparedQuery<E, R> implements DelegatePreparedQuery<E, R>, MongoPreparedQuery<E, R> {

    private final PreparedQuery<E, R> preparedQuery;
    private final MongoStoredQuery<E, R> mongoStoredQuery;

    public DefaultMongoPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        this.preparedQuery = preparedQuery;
        this.mongoStoredQuery = (MongoStoredQuery<E, R>) ((DelegateStoredQuery<Object, Object>) preparedQuery).getStoredQueryDelegate();
    }

    @Override
    public PreparedQuery<E, R> getPreparedQueryDelegate() {
        return preparedQuery;
    }

    @Override
    public BsonDocument getFilter() {
        BsonDocument filter = mongoStoredQuery.getFilter();
        return filter == null ? null : filter.clone();
    }

    @Override
    public BsonArray getPipeline() {
        BsonArray pipeline = mongoStoredQuery.getPipeline();
        return pipeline == null ? null : pipeline.clone();
    }

    @Override
    public BsonDocument getUpdate() {
        BsonDocument update = mongoStoredQuery.getUpdate();
        return update == null ? null : update.clone();
    }
}
