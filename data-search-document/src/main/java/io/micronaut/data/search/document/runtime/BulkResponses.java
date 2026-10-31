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
import io.micronaut.data.exceptions.OptimisticLockException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

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
     * @param status Reads an item's HTTP status; a 409 is a version conflict
     * @param <I> The item type of the client library
     * @return The ids, in request order
     * @throws OptimisticLockException When an item's version did not match
     */
    public static <I> List<String> ids(String store, List<I> items, Function<I, @Nullable String> error, Function<I, String> id, ToIntFunction<I> status) {
        List<String> ids = new ArrayList<>(items.size());
        for (I item : items) {
            String reason = error.apply(item);
            if (reason != null) {
                if (status.applyAsInt(item) == 409) {
                    throw new OptimisticLockException("Document " + id.apply(item) + " was modified concurrently: " + reason);
                }
                throw new DataAccessException(store + " bulk item failed: " + reason);
            }
            ids.add(id.apply(item));
        }
        return ids;
    }
}
