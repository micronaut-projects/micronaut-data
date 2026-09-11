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
package io.micronaut.data.nitrite.runtime.read;

import io.micronaut.core.annotation.Internal;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.common.RecordStream;
import org.dizitart.no2.common.SortableFields;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Bounds how many documents a cursor yields, for a read whose bound should not be given to Nitrite.
 *
 * <p>For MVStore and in-memory storage, a find that carries a row bound and a sort is ordered from
 * an index on the sort field rather than blocking-sorted. That path measured about 3.4 times slower
 * than the blocking sort on every collection size and bound tried, at a cost that grows with the
 * collection rather than with the bound, so a sorted read is issued unbounded and bounded here
 * instead. RocksDB is an exception: its bounded indexed path is faster in the benchmark, so its
 * bound remains on the find. A skip is unaffected and stays with the find, which makes the cursor
 * window only ever drop a suffix of the cursor.
 *
 * @since 5.2.0
 */
@Internal
public final class CursorWindow {

    private CursorWindow() {
    }

    /**
     * Takes the row bound off a sorted find when the backend benefits from cursor-side limiting.
     *
     * @param options the find options, whose bound is cleared when the find is sorted
     * @param useCursorLimit whether to move a sorted bound from the find to the cursor
     * @return the withheld bound, or {@code -1} when the find should retain its bound
     */
    public static long withholdSortedLimit(FindOptions options, boolean useCursorLimit) {
        if (!useCursorLimit) {
            return -1;
        }
        Long limit = options.limit();
        SortableFields orderBy = options.orderBy();
        if (limit == null || limit <= 0 || orderBy == null || orderBy.getSortingOrders().isEmpty()) {
            return -1;
        }
        options.limit((Long) null);
        return limit;
    }

    /**
     * Bounds a cursor to at most the given number of documents.
     *
     * @param cursor     the cursor to bound
     * @param maxResults the greatest number of documents to yield; a value of zero or less leaves
     *                   the cursor unbounded
     * @return the bounded cursor, or the original one when no bound applies
     */
    public static RecordStream<Document> limit(RecordStream<Document> cursor, long maxResults) {
        if (maxResults <= 0) {
            return cursor;
        }
        return () -> new Iterator<>() {

            private final Iterator<Document> delegate = cursor.iterator();
            private long remaining = maxResults;

            @Override
            public boolean hasNext() {
                return remaining > 0 && delegate.hasNext();
            }

            @Override
            public Document next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                remaining--;
                return delegate.next();
            }
        };
    }
}
