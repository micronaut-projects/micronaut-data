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
package io.micronaut.data.graph.runtime.operations;

import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Limits that apply to every graph repository.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@ConfigurationProperties(GraphOperationsConfiguration.PREFIX)
public class GraphOperationsConfiguration {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "micronaut.data.graph";

    /**
     * The default maximum depth of a path query.
     */
    public static final int DEFAULT_MAX_PATH_DEPTH = 10;

    /**
     * The default number of generated queries kept per repository operations bean.
     */
    public static final int DEFAULT_QUERY_CACHE_SIZE = 1024;

    private int maxPathDepth = DEFAULT_MAX_PATH_DEPTH;
    private int queryCacheSize = DEFAULT_QUERY_CACHE_SIZE;

    /**
     * The largest depth a {@code @PathLimit} argument may ask for. A variable-length path visits
     * every path up to its depth, so a caller-supplied depth is capped. Default {@value #DEFAULT_MAX_PATH_DEPTH}.
     *
     * @return The maximum depth
     */
    public int getMaxPathDepth() {
        return maxPathDepth;
    }

    /**
     * @param maxPathDepth The maximum depth
     */
    public void setMaxPathDepth(int maxPathDepth) {
        this.maxPathDepth = maxPathDepth;
    }

    /**
     * The number of generated queries each repository operations bean keeps. Writes of entities
     * with schemaless properties generate a query per set of property names, so the cache is
     * bounded. Default {@value #DEFAULT_QUERY_CACHE_SIZE}.
     *
     * @return The cache size
     */
    public int getQueryCacheSize() {
        return queryCacheSize;
    }

    /**
     * @param queryCacheSize The cache size
     */
    public void setQueryCacheSize(int queryCacheSize) {
        this.queryCacheSize = queryCacheSize;
    }
}
