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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.mongodb.operations.options.MongoAggregationOptions;
import org.bson.conversions.Bson;

import java.util.List;

/**
 * The MongoDB's aggregation command.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
@Experimental
public final class MongoAggregation {

    private final List<Bson> pipeline;
    private final MongoAggregationOptions options;

    /**
     * The default constructor.
     *
     * @param pipeline The pipeline
     * @param options  The options
     */
    public MongoAggregation(@NonNull List<Bson> pipeline, @NonNull MongoAggregationOptions options) {
        this.pipeline = pipeline;
        this.options = options;
    }

    @NonNull
    public List<Bson> getPipeline() {
        return pipeline;
    }

    @NonNull
    public MongoAggregationOptions getOptions() {
        return options;
    }
}
