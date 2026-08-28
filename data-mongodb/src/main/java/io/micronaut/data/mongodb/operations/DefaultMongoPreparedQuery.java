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
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Pageable.Mode;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.mongodb.operations.options.MongoFindOptions;
import io.micronaut.data.runtime.operations.internal.query.DefaultBindableParametersPreparedQuery;
import io.micronaut.data.runtime.query.internal.DefaultPreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegatePreparedQuery;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link MongoPreparedQuery}.
 *
 * @param <E>   The entity type
 * @param <R>   The result type
 * @author Denis Stepanov
 * @since 3.3.
 */
@Internal
final class DefaultMongoPreparedQuery<E, R> extends DefaultBindableParametersPreparedQuery<E, R> implements DelegatePreparedQuery<E, R>, MongoPreparedQuery<E, R> {

    private static final String NULL_RANK_FIELD_PREFIX = "__micronaut_nulls_";

    private final DefaultPreparedQuery<E, R> defaultPreparedQuery;
    private final MongoStoredQuery<E, R> mongoStoredQuery;

    public DefaultMongoPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        super(preparedQuery);
        this.defaultPreparedQuery = (DefaultPreparedQuery<E, R>) preparedQuery;
        this.mongoStoredQuery = (MongoStoredQuery<E, R>) ((DelegateStoredQuery<Object, Object>) preparedQuery).getStoredQueryDelegate();
    }

    @Override
    public RuntimePersistentEntity<E> getPersistentEntity() {
        return mongoStoredQuery.getRuntimePersistentEntity();
    }

    @Override
    public boolean isAggregate() {
        return mongoStoredQuery.isAggregate();
    }

    @Override
    public Limit getQueryLimit() {
        Limit queryLimit = super.getQueryLimit();
        if (queryLimit.isLimited()) {
            return queryLimit;
        }
        return getParameterInRole(TypeRole.LIMIT, Limit.class).orElse(queryLimit);
    }

    @Override
    public Sort getSort() {
        return super.getSort();
    }

    @Override
    public MongoAggregation getAggregation() {
        MongoAggregation aggregation = mongoStoredQuery.getAggregation(defaultPreparedQuery.getContext());
        Pageable pageable = getPageable();
        if (!pageable.isUnpaged()) {
            List<Bson> pipeline = new ArrayList<>(aggregation.getPipeline());
            applyPageable(pageable, pipeline);
            return new MongoAggregation(pipeline, aggregation.getOptions());
        }
        Limit limit = getQueryLimit();
        Sort sort = getSort();
        if (limit.isLimited() || sort.isSorted()) {
            List<Bson> pipeline = new ArrayList<>(aggregation.getPipeline());
            applyPageable(limit, sort, pipeline);
            return new MongoAggregation(pipeline, aggregation.getOptions());
        }
        return aggregation;
    }

    @Override
    public MongoFind getFind() {
        MongoFind find = mongoStoredQuery.getFind(defaultPreparedQuery.getContext());
        Pageable pageable = defaultPreparedQuery.getPageable();
        if (!pageable.isUnpaged()) {
            if (pageable.getMode() != Mode.OFFSET) {
                throw new UnsupportedOperationException("Mode " + pageable.getMode() + " is not supported by the MongoDB implementation");
            }
            Limit limit = pageable.getLimit();
            Sort sort = pageable.getSort();
            return applyLimitAndSort(find, limit, sort);
        }
        Limit limit = getQueryLimit();
        Sort sort = getSort();
        if (limit.isLimited() || sort.isSorted()) {
            return applyLimitAndSort(find, limit, sort);
        }
        return find;
    }

    private MongoFind applyLimitAndSort(MongoFind find, Limit limit, Sort sort) {
        MongoFindOptions findOptions = find.getOptions();
        MongoFindOptions options = findOptions == null ? new MongoFindOptions() : new MongoFindOptions(findOptions);
        options.limit(limit.maxResults()).skip((int) limit.offset());
        if (sort.isSorted()) {
            Bson sortBson = getSort(sort);
            options.sort(sortBson);
        }
        return new MongoFind(options);
    }

    private Bson getSort(Sort sort) {
        List<Bson> sorts = new ArrayList<>();
        int nullRankIndex = 0;
        for (Sort.Order order : sort.getOrderBy()) {
            String property = resolveSortField(order);
            if (order.getNullOrdering() != Sort.Order.NullOrdering.NONE) {
                // Sorting the rank ascending places the nulls where the caller asked for them
                sorts.add(Sorts.ascending(NULL_RANK_FIELD_PREFIX + nullRankIndex++));
            }
            sorts.add(order.isAscending() ? Sorts.ascending(property) : Sorts.descending(property));
        }
        return Sorts.orderBy(sorts);
    }

    private String resolveSortField(Sort.Order order) {
        RuntimePersistentEntity<E> persistentEntity = getPersistentEntity();
        String property = order.getProperty();
        if (persistentEntity.hasIdentity() && persistentEntity.getIdentity().getName().contains(property)) {
            return MongoUtils.ID;
        }
        return property;
    }

    /**
     * Builds the {@code $addFields} document ranking each document by whether the field it is sorted on is
     * null or missing. MongoDB always orders null and missing values first when ascending and last when
     * descending, so an explicit null ordering has to be expressed as a computed field to sort on first.
     *
     * @param sort The sort
     * @return The fields to add, empty when no order asks for an explicit null ordering
     */
    private BsonDocument getNullRankFields(Sort sort) {
        BsonDocument nullRankFields = new BsonDocument();
        int nullRankIndex = 0;
        for (Sort.Order order : sort.getOrderBy()) {
            Sort.Order.NullOrdering nullOrdering = order.getNullOrdering();
            if (nullOrdering == Sort.Order.NullOrdering.NONE) {
                continue;
            }
            int nullRank = nullOrdering == Sort.Order.NullOrdering.FIRST ? 0 : 1;
            nullRankFields.append(NULL_RANK_FIELD_PREFIX + nullRankIndex++, new BsonDocument().append("$cond",
                new BsonArray(List.of(
                    new BsonDocument().append("$in", new BsonArray(List.of(
                        new BsonDocument().append("$type", new BsonString("$" + resolveSortField(order))),
                        new BsonArray(List.of(new BsonString("missing"), new BsonString("null")))
                    ))),
                    new BsonInt32(nullRank),
                    new BsonInt32(1 - nullRank)
                ))));
        }
        return nullRankFields;
    }

    @Override
    public MongoUpdate getUpdateMany() {
        return mongoStoredQuery.getUpdateMany(defaultPreparedQuery.getContext());
    }

    @Override
    public MongoFindOneAndUpdate getFindOneAndUpdate() {
        return mongoStoredQuery.getFindOneAndUpdate(defaultPreparedQuery.getContext());
    }

    @Override
    public MongoDelete getDeleteMany() {
        return mongoStoredQuery.getDeleteMany(defaultPreparedQuery.getContext());
    }

    @Override
    public PreparedQuery<E, R> getPreparedQueryDelegate() {
        return defaultPreparedQuery;
    }

    private void applyPageable(Pageable pageable, List<Bson> pipeline) {
        if (pageable.getMode() != Mode.OFFSET) {
            throw new UnsupportedOperationException("Mode " + pageable.getMode() + " is not supported by the MongoDB implementation");
        }
        applyPageable(pageable.getLimit(), pageable.getSort(), pipeline);
    }

    private void applyPageable(Limit queryLimit, Sort sort, List<Bson> pipeline) {
        if (queryLimit.isLimited()) {
            // The criteria query builder bakes the limit into the pipeline from the very same
            // parameter we are about to apply, so drop its stages rather than paginating twice
            removeTrailingPaginationStages(pipeline);
        }
        if (sort.isSorted()) {
            BsonDocument existingSortBson = null;
            for (Bson p : pipeline) {
                BsonDocument sortBsonDocument = p.toBsonDocument();
                if (sortBsonDocument != null) {
                    BsonValue bsonValue = sortBsonDocument.get("$sort");
                    if (bsonValue != null) {
                        existingSortBson = bsonValue.asDocument();
                        if (existingSortBson != null) {
                            break;
                        }
                    }
                }
            }
            BsonDocument nullRankFields = getNullRankFields(sort);
            Bson sortBson = getSort(sort);
            if (existingSortBson != null) {
                existingSortBson.putAll(sortBson.toBsonDocument());
            } else {
                BsonDocument sortStage = new BsonDocument().append("$sort", sortBson.toBsonDocument());
                addStageToPipelineBefore(pipeline, sortStage, "$limit", "$skip");
            }
            if (!nullRankFields.isEmpty()) {
                addStageToPipelineBefore(pipeline, new BsonDocument().append("$addFields", nullRankFields), "$sort");
                BsonArray unset = new BsonArray();
                nullRankFields.keySet().forEach(field -> unset.add(new BsonString(field)));
                pipeline.add(new BsonDocument().append("$unset", unset));
            }
        }
        if (queryLimit.isLimited()) {
            int offset = (int) queryLimit.offset();
            if (offset > 0) {
                pipeline.add(new BsonDocument().append("$skip", new BsonInt32(offset)));
            }
            int maxResults = queryLimit.maxResults();
            if (maxResults > 0) {
                pipeline.add(new BsonDocument().append("$limit", new BsonInt32(maxResults)));
            }
        }
    }

    /**
     * Removes the {@code $skip} and {@code $limit} stages the query builder appended to the end of the
     * pipeline. Only trailing stages are removed so that pagination inside a user supplied aggregation
     * is left alone.
     *
     * @param pipeline The pipeline
     */
    private void removeTrailingPaginationStages(List<Bson> pipeline) {
        for (int i = pipeline.size() - 1; i >= 0; i--) {
            BsonDocument stage = pipeline.get(i).toBsonDocument();
            if (stage == null || !(stage.containsKey("$skip") || stage.containsKey("$limit"))) {
                return;
            }
            pipeline.remove(i);
        }
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
