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
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import org.bson.BsonArray;
import org.bson.BsonDocument;

/**
 * Default implementation of {@link MongoStoredQuery}.
 *
 * @param <E> The entity type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 3.3.
 */
@Internal
final class DefaultMongoStoredQuery<E, R> implements DelegateStoredQuery<E, R>, MongoStoredQuery<E, R> {

    private final StoredQuery<E, R> storedQuery;
    private final BsonDocument update;
    private final BsonArray pipeline;
    private final BsonDocument filter;

    DefaultMongoStoredQuery(StoredQuery<E, R> storedQuery) {
        this(storedQuery, storedQuery.getAnnotationMetadata().stringValue(Query.class, "update").orElse(null));
    }

    DefaultMongoStoredQuery(StoredQuery<E, R> storedQuery, String update) {
        this.storedQuery = storedQuery;
        this.update = update == null ? null : BsonDocument.parse(update);
        String query = storedQuery.getQuery();
        if (StringUtils.isEmpty(query)) {
            pipeline = null;
            filter = null;
        } else if (query.startsWith("[")) {
            pipeline = BsonArray.parse(query);
            filter = null;
        } else {
            pipeline = null;
            filter = BsonDocument.parse(query);
        }
    }

    @Override
    public BsonDocument getUpdate() {
        return update;
    }

    @Override
    public BsonDocument getFilter() {
        return filter;
    }

    @Override
    public BsonArray getPipeline() {
        return pipeline;
    }

    @Override
    public StoredQuery<E, R> getStoredQueryDelegate() {
        return storedQuery;
    }
}
