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
package io.micronaut.data.search.document.conf;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.runtime.config.SchemaGenerate;

/**
 * Configuration of the document search stores.
 *
 * @since 5.4.0
 */
@ConfigurationProperties(DocumentSearchConfiguration.PREFIX)
public class DocumentSearchConfiguration {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "micronaut.data.search";

    /**
     * The property enabling index creation on startup.
     */
    public static final String CREATE_INDEXES_PROPERTY = PREFIX + ".create-indexes";

    /**
     * The default of {@link #getMaxResults()}.
     */
    public static final int DEFAULT_MAX_RESULTS = 10_000;

    @Nullable
    private String indexPrefix;
    private Refresh refresh = Refresh.WAIT_FOR;
    private boolean createIndexes = true;
    private SchemaGenerate schemaGenerate = SchemaGenerate.CREATE;
    private int maxResults = DEFAULT_MAX_RESULTS;

    /**
     * @return A prefix added to every index name, for per-environment naming
     */
    @Nullable
    public String getIndexPrefix() {
        return indexPrefix;
    }

    /**
     * @param indexPrefix A prefix added to every index name. It is trusted configuration that goes into
     *                    request paths; the resulting names are checked against the index naming rules
     */
    public void setIndexPrefix(@Nullable String indexPrefix) {
        this.indexPrefix = indexPrefix;
    }

    /**
     * @return How writes are made visible to searches. Defaults to {@link Refresh#WAIT_FOR}
     */
    public Refresh getRefresh() {
        return refresh;
    }

    /**
     * @param refresh How writes are made visible to searches
     */
    public void setRefresh(Refresh refresh) {
        this.refresh = refresh;
    }

    /**
     * @return Whether indexes of searchable entities are created on startup. Defaults to true
     */
    public boolean isCreateIndexes() {
        return createIndexes;
    }

    /**
     * @return How the indexes of searchable entities are generated on startup: {@code CREATE} (the default)
     * creates the missing ones, {@code CREATE_DROP} drops and recreates them, deleting the stored documents,
     * and {@code NONE} leaves them alone
     */
    public SchemaGenerate getSchemaGenerate() {
        return createIndexes ? schemaGenerate : SchemaGenerate.NONE;
    }

    /**
     * @param schemaGenerate How the indexes are generated on startup
     */
    public void setSchemaGenerate(SchemaGenerate schemaGenerate) {
        this.schemaGenerate = schemaGenerate;
    }

    /**
     * @param createIndexes Whether indexes are created on startup
     */
    public void setCreateIndexes(boolean createIndexes) {
        this.createIndexes = createIndexes;
    }

    /**
     * @return The most hits a query without a {@code Pageable} or limit fetches and deserializes,
     * {@value #DEFAULT_MAX_RESULTS} by default, the stores' default {@code index.max_result_window}
     */
    public int getMaxResults() {
        return maxResults;
    }

    /**
     * @param maxResults The most hits an unpaged query fetches; a lower value bounds the memory a query
     *                   can use, a higher one needs a larger {@code index.max_result_window} on the index
     */
    public void setMaxResults(int maxResults) {
        if (maxResults < 1) {
            throw new IllegalArgumentException("micronaut.data.search.max-results must be positive, got " + maxResults);
        }
        this.maxResults = maxResults;
    }

    /**
     * The refresh policy of writes.
     */
    public enum Refresh {
        /**
         * Return at once; the write becomes searchable on the next periodic refresh.
         */
        NONE,
        /**
         * Wait until the write is searchable.
         */
        WAIT_FOR,
        /**
         * Refresh the shard at once.
         */
        TRUE
    }
}
