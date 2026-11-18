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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Reads the outcome of a bulk request the same way for every client library.
 *
 * @since 5.3.0
 */
@Internal
public final class BulkResponses {

    private BulkResponses() {
    }

    /**
     * Collects the ids of the indexed documents, failing when the store rejected any item. A bulk
     * request is not atomic: the {@link BulkIndexException} names every rejected item and how many
     * others were written, and carries the ids of the written ones.
     *
     * @param store The store name, for the error message
     * @param items The response items, in request order
     * @param error Reads an item's error reason, {@code null} when it succeeded
     * @param id Reads an item's document id
     * @param <I> The item type of the client library
     * @return The ids, in request order
     */
    public static <I> List<String> ids(String store, List<I> items, Function<I, @Nullable String> error, Function<I, String> id) {
        List<@Nullable String> ids = new ArrayList<>(items.size());
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            I item = items.get(i);
            String reason = error.apply(item);
            if (reason != null) {
                failures.add("item " + i + " (id " + id.apply(item) + "): " + reason);
                ids.add(null);
            } else {
                ids.add(id.apply(item));
            }
        }
        if (!failures.isEmpty()) {
            throw new BulkIndexException(store + " bulk request rejected " + failures.size() + " of " + items.size()
                + " documents, the other " + (items.size() - failures.size()) + " were written: " + String.join("; ", failures), ids);
        }
        return (List<String>) (List<?>) ids;
    }

    /**
     * The failure of a bulk index sent as one write per document, which stops at the first failure.
     *
     * @param written The ids of the documents written before the failure
     * @param total The number of documents of the request
     * @param cause The failure
     * @return The exception
     */
    static BulkIndexException stopped(List<String> written, int total, Throwable cause) {
        List<@Nullable String> ids = new ArrayList<>(written);
        while (ids.size() < total) {
            ids.add(null);
        }
        return new BulkIndexException("Indexing document " + written.size() + " of " + total + " failed, the "
            + written.size() + " before it were written and the rest were not sent: " + cause.getMessage(), ids, cause);
    }
}
