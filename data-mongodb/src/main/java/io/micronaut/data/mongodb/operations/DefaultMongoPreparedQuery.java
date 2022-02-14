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

import com.mongodb.client.model.Sorts;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.mongodb.operations.options.MongoFindOptions;
import io.micronaut.data.runtime.query.internal.DefaultPreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link MongoPreparedQuery}.
 *
 * @param <E>   The entity type
 * @param <R>   The result type
 * @param <Dtb> The database type
 * @author Denis Stepanov
 * @since 3.3.
 */
@Internal
final class DefaultMongoPreparedQuery<E, R, Dtb> implements DelegatePreparedQuery<E, R>, MongoPreparedQuery<E, R, Dtb> {

    private final DefaultPreparedQuery<E, R> preparedQuery;
    private final MongoStoredQuery<E, R, Dtb> mongoStoredQuery;

    public DefaultMongoPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        this.preparedQuery = (DefaultPreparedQuery<E, R>) preparedQuery;
        this.mongoStoredQuery = (MongoStoredQuery<E, R, Dtb>) ((DelegateStoredQuery<Object, Object>) preparedQuery).getStoredQueryDelegate();
    }

    @Override
    public RuntimePersistentEntity<E> getRuntimePersistentEntity() {
        return mongoStoredQuery.getRuntimePersistentEntity();
    }

    @Override
    public Dtb getDatabase() {
        return mongoStoredQuery.getDatabase();
    }

    @Override
    public boolean isAggregate() {
        return mongoStoredQuery.isAggregate();
    }

    @Override
    public MongoAggregation getAggregation() {
        MongoAggregation aggregation = mongoStoredQuery.getAggregation(preparedQuery.getContext());
        Pageable pageable = getPageable();
        if (pageable != Pageable.UNPAGED) {
            List<Bson> pipeline = new ArrayList<>(aggregation.getPipeline());
            applyPageable(pageable, pipeline);
            return new MongoAggregation(pipeline, aggregation.getOptions());
        }
        return aggregation;
    }

    @Override
    public MongoFind getFind() {
        MongoFind find = mongoStoredQuery.getFind(preparedQuery.getContext());
        Pageable pageable = preparedQuery.getPageable();
        if (pageable != Pageable.UNPAGED) {
            MongoFindOptions options = new MongoFindOptions(find.getOptions());
            options.limit(pageable.getSize()).skip((int) pageable.getOffset());
            Sort pageableSort = pageable.getSort();
            if (pageableSort.isSorted()) {
                Bson sort = pageableSort.getOrderBy().stream().map(order -> order.isAscending() ? Sorts.ascending(order.getProperty()) : Sorts.descending(order.getProperty()))
                        .collect(Collectors.collectingAndThen(Collectors.toList(), Sorts::orderBy));
                options.sort(sort);
            }
            return new MongoFind(options);
        }
        return find;
    }

    @Override
    public MongoUpdate getUpdateMany() {
        return mongoStoredQuery.getUpdateMany(preparedQuery.getContext());
    }

    @Override
    public MongoDelete getDeleteMany() {
        return mongoStoredQuery.getDeleteMany(preparedQuery.getContext());
    }

    @Override
    public PreparedQuery<E, R> getPreparedQueryDelegate() {
        return preparedQuery;
    }

    private int applyPageable(Pageable pageable, List<Bson> pipeline) {
        int limit = 0;
        if (pageable != Pageable.UNPAGED) {
            int skip = (int) pageable.getOffset();
            limit = pageable.getSize();
            Sort pageableSort = pageable.getSort();
            if (pageableSort.isSorted()) {
                Bson sort = pageableSort.getOrderBy().stream().map(order -> order.isAscending() ? Sorts.ascending(order.getProperty()) : Sorts.descending(order.getProperty())).collect(Collectors.collectingAndThen(Collectors.toList(), Sorts::orderBy));
                BsonDocument sortStage = new BsonDocument().append("$sort", sort.toBsonDocument());
                addStageToPipelineBefore(pipeline, sortStage, "$limit", "$skip");
            }
            if (skip > 0) {
                pipeline.add(new BsonDocument().append("$skip", new BsonInt32(skip)));
            }
            if (limit > 0) {
                pipeline.add(new BsonDocument().append("$limit", new BsonInt32(limit)));
            }
        }
        return limit;
    }

    private void addStageToPipelineBefore(List<Bson> pipeline, BsonDocument stageToAdd, String... beforeStages) {
        int lastFoundIndex = -1;
        int index = 0;
        for (Bson stage : pipeline) {
            for (String beforeStageName : beforeStages) {
                if (stage.toBsonDocument().containsKey(beforeStageName)) {
                    lastFoundIndex = index;
                    break;
                }
            }
            index++;
        }
        if (lastFoundIndex > -1) {
            pipeline.add(lastFoundIndex, stageToAdd);
        } else {
            pipeline.add(stageToAdd);
        }
    }
}
