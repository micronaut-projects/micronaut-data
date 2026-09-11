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
package io.micronaut.data.nitrite.operations;

import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.NitriteCollection;

import static org.dizitart.no2.index.IndexOptions.indexOptions;
import static org.dizitart.no2.index.IndexType.UNIQUE;

/**
 * Manages indexes for one Micronaut Data Nitrite datasource.
 *
 * <p>This is the provider boundary for index DDL. Applications identify a collection and its
 * persisted field by name, while all Nitrite API types and index options remain inside the
 * Micronaut Data adapter. Operations are intentionally idempotent where possible so a failed or
 * interrupted rebuild can be retried on the next run.
 *
 * <p>Index DDL is not part of a Nitrite data transaction, so an index dropped for the duration of
 * a bulk load is not restored by rolling that load back. {@link #dropUniqueIndex} returns an
 * {@link IndexScope} for that case: it rebuilds the index when the scope closes, whether the work
 * inside it succeeded or threw.
 *
 * @since 5.2.0
 */
public final class NitriteIndexManagement {

    private final Nitrite database;

    /**
     * Creates index management bound to one Nitrite datasource.
     *
     * @param database the Nitrite database of this datasource
     */
    public NitriteIndexManagement(Nitrite database) {
        this.database = database;
    }

    /**
     * Drops an index on a single field if it exists.
     *
     * @param collectionName the Nitrite collection name
     * @param fieldName the persisted field indexed by Nitrite
     */
    public void dropIndex(String collectionName, String fieldName) {
        NitriteCollection collection = database.getCollection(collectionName);
        if (collection.hasIndex(fieldName)) {
            collection.dropIndex(fieldName);
        }
    }

    /**
     * Rebuilds a unique index on a single field in one database-side index build.
     *
     * <p>If an index already exists, it is dropped first so this method guarantees that the
     * resulting index is unique rather than merely leaving an existing index untouched.
     *
     * @param collectionName the Nitrite collection name
     * @param fieldName the persisted field indexed by Nitrite
     */
    public void rebuildUniqueIndex(String collectionName, String fieldName) {
        NitriteCollection collection = database.getCollection(collectionName);
        if (collection.hasIndex(fieldName)) {
            collection.dropIndex(fieldName);
        }
        collection.createIndex(indexOptions(UNIQUE), fieldName);
    }

    /**
     * Drops an existing unique index and returns a scope that rebuilds it when closed.
     *
     * <p>If the index was not present when this method starts, the scope is a no-op. This preserves
     * the datasource's {@code create-indexes=false} configuration while still making the normal
     * indexed case safe across exceptions.
     *
     * @param collectionName the Nitrite collection name
     * @param fieldName the persisted field indexed by Nitrite
     * @return a closeable scope that restores the index if it was present
     */
    public IndexScope dropUniqueIndex(String collectionName, String fieldName) {
        NitriteCollection collection = database.getCollection(collectionName);
        boolean indexed = collection.hasIndex(fieldName);
        try {
            if (indexed) {
                collection.dropIndex(fieldName);
            }
        } catch (RuntimeException e) {
            if (indexed && !collection.hasIndex(fieldName)) {
                // The drop got far enough to remove the index before failing. Put it back rather
                // than leaving the collection unindexed, and report the original failure.
                try {
                    rebuildUniqueIndex(collectionName, fieldName);
                } catch (RuntimeException rebuildFailure) {
                    e.addSuppressed(rebuildFailure);
                }
            }
            throw e;
        }
        return new IndexScope(collectionName, fieldName, indexed);
    }

    /**
     * A try-with-resources scope for a temporarily dropped unique index.
     */
    public final class IndexScope implements AutoCloseable {

        private final String collectionName;
        private final String fieldName;
        private final boolean indexed;
        private boolean closed;

        private IndexScope(String collectionName, String fieldName, boolean indexed) {
            this.collectionName = collectionName;
            this.fieldName = fieldName;
            this.indexed = indexed;
        }

        /**
         * Rebuilds the index if it existed when this scope was opened.
         */
        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (indexed) {
                    rebuildUniqueIndex(collectionName, fieldName);
                }
            }
        }
    }
}
