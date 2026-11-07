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
package io.micronaut.data.model.search;

import io.micronaut.core.annotation.Experimental;

/**
 * The syntax of a {@link SearchQuery} string.
 *
 * @since 5.4.0
 */
@Experimental
public enum SearchSyntax {
    /**
     * The portable grammar: words, quoted phrases, {@code -} negation and {@code OR}, matched under the
     * query's {@link MatchMode}. The default.
     */
    SIMPLE,
    /**
     * The store's own query syntax, passed through unparsed; see {@link MatchMode#NATIVE}.
     *
     * <p>Only pass trusted input: the native syntax is not escaped, so a user could widen the query.
     * On Elasticsearch and OpenSearch {@code field:value} reaches fields outside the search fields;
     * leading wildcards are disabled there.</p>
     */
    NATIVE
}
