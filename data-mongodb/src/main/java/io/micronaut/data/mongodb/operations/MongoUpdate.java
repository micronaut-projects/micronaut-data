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

import com.mongodb.client.model.UpdateOptions;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import org.bson.conversions.Bson;

/**
 * The MongoDB's many command.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
@Experimental
public final class MongoUpdate {

    private final Bson update;
    private final Bson filter;
    private final UpdateOptions options;

    /**
     * The default constructor.
     *
     * @param update  The update
     * @param filter  The filter
     * @param options The options
     */
    public MongoUpdate(@NonNull Bson update, @NonNull Bson filter, @NonNull UpdateOptions options) {
        this.update = update;
        this.filter = filter;
        this.options = options;
    }

    public Bson getUpdate() {
        return update;
    }

    public Bson getFilter() {
        return filter;
    }

    public UpdateOptions getOptions() {
        return options;
    }
}
