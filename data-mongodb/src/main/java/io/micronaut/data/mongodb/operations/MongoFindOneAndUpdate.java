/*
 * Copyright 2017-2026 original authors
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

import com.mongodb.client.model.FindOneAndUpdateOptions;
import io.micronaut.core.annotation.Experimental;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB single-document update definition that returns a document.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Experimental
public final class MongoFindOneAndUpdate {

    private final Bson update;
    private final Bson filter;
    private final FindOneAndUpdateOptions options;

    /**
     * Creates a MongoDB single-document update command that returns a document.
     *
     * @param update The update document
     * @param filter The filter document
     * @param options The execution options
     * @since 5.0.0
     */
    public MongoFindOneAndUpdate(@NonNull Bson update, @Nullable Bson filter, @NonNull FindOneAndUpdateOptions options) {
        this.update = update;
        this.filter = filter == null ? new BsonDocument() : filter;
        this.options = options;
    }

    /**
     * @return The update document
     */
    public Bson getUpdate() {
        return update;
    }

    /**
     * @return The filter document
     */
    public Bson getFilter() {
        return filter;
    }

    /**
     * @return The execution options
     */
    public FindOneAndUpdateOptions getOptions() {
        return options;
    }
}
