/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.mongodb.repository;

import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.UpdateOptions;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.mongodb.operations.options.MongoAggregationOptions;
import io.micronaut.data.mongodb.operations.options.MongoFindOptions;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB specific repository that allows to use direct BSON objects.
 *
 * @param <E> The entity type
 * @author Denis Stepanov
 * @since 3.3
 */
public interface MongoQueryExecutor<E> {

    /**
     * Finds one result.
     *
     * @param filter The filter to be applied
     * @return The optional result
     */
    Optional<E> findOne(@Nullable Bson filter);

    /**
     * Finds one result.
     *
     * @param options The options
     * @return The optional result
     */
    Optional<E> findOne(@NonNull MongoFindOptions options);

    /**
     * Finds all results.
     *
     * @param filter The filter to be applied
     * @return The records
     */
    @NonNull
    List<E> findAll(@Nullable Bson filter);

    /**
     * Finds all results.
     *
     * @param options The options
     * @return The records
     */
    @NonNull
    List<E> findAll(@NonNull MongoFindOptions options);

    /**
     * Finds a page of records.
     *
     * @param filter   The filter
     * @param pageable The pageable
     * @return The page
     */
    @NonNull
    Page<E> findAll(@Nullable Bson filter, @NonNull Pageable pageable);

    /**
     * Finds a page of records.
     *
     * @param options  The options
     * @param pageable The pageable
     * @return The page
     */
    @NonNull
    Page<E> findAll(@NonNull MongoFindOptions options, @NonNull Pageable pageable);

    /**
     * Finds a page of records.
     *
     * @param pipeline The pipeline to be applied
     * @return The optional result
     */
    Optional<E> findOne(@NonNull Iterable<Bson> pipeline);

    /**
     * Finds one result.
     *
     * @param pipeline The pipeline to be applied
     * @param options  The aggregation options
     * @return The optional result
     */
    Optional<E> findOne(@NonNull Iterable<Bson> pipeline, @NonNull MongoAggregationOptions options);

    /**
     * Finds all results.
     *
     * @param pipeline The pipeline to be applied
     * @return The results
     */
    @NonNull
    List<E> findAll(@NonNull Iterable<Bson> pipeline);

    /**
     * Finds all results.
     *
     * @param pipeline The pipeline to be applied
     * @param options  The options
     * @return The results
     */
    @NonNull
    List<E> findAll(@NonNull Iterable<Bson> pipeline, @NonNull MongoAggregationOptions options);

    /**
     * Count the records.
     *
     * @param filter The filter to be applied
     * @return The count
     */
    long count(@Nullable Bson filter);

    /**
     * Delete the records matching the filter.
     *
     * @param filter The filter to be applied
     * @return The deleted count
     */
    long deleteAll(@NonNull Bson filter);

    /**
     * Delete the records matching the filter.
     *
     * @param filter  The filter to be applied
     * @param options The delete options
     * @return The deleted count
     */
    long deleteAll(@NonNull Bson filter, @NonNull DeleteOptions options);

    /**
     * Update the records matching the filter.
     *
     * @param filter The filter to be applied
     * @param update The update modification
     * @return The updated count
     */
    long updateAll(@NonNull Bson filter, @NonNull Bson update);

    /**
     * Update the records matching the filter.
     *
     * @param filter  The filter to be applied
     * @param update  The update modification
     * @param options The update options
     * @return The updated count
     */
    long updateAll(@NonNull Bson filter, @NonNull Bson update, @NonNull UpdateOptions options);
}
