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

    @Nullable
    private String indexPrefix;
    private Refresh refresh = Refresh.WAIT_FOR;
    private boolean createIndexes = true;

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
     * @param createIndexes Whether indexes are created on startup
     */
    public void setCreateIndexes(boolean createIndexes) {
        this.createIndexes = createIndexes;
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
