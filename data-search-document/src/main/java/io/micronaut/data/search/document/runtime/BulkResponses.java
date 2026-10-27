/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.search.document.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Reads the outcome of a bulk request the same way for every client library.
 *
 * @since 5.4.0
 */
@Internal
public final class BulkResponses {

    private BulkResponses() {
    }

    /**
     * Collects the ids of the indexed documents, failing on the first item the store rejected.
     *
     * @param store The store name, for the error message
     * @param items The response items, in request order
     * @param error Reads an item's error reason, {@code null} when it succeeded
     * @param id Reads an item's document id
     * @param <I> The item type of the client library
     * @return The ids, in request order
     */
    public static <I> List<String> ids(String store, List<I> items, Function<I, @Nullable String> error, Function<I, String> id) {
        List<String> ids = new ArrayList<>(items.size());
        for (I item : items) {
            String reason = error.apply(item);
            if (reason != null) {
                throw new DataAccessException(store + " bulk item failed: " + reason);
            }
            ids.add(id.apply(item));
        }
        return ids;
    }
}
