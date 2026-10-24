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
package io.micronaut.data.annotation.search;

import io.micronaut.core.annotation.Experimental;

/**
 * How a {@link SearchField} is indexed.
 *
 * @since 5.4.0
 */
@Experimental
public enum SearchFieldType {
    /**
     * Analysed text: tokenised, stemmed and matched term by term.
     */
    TEXT,
    /**
     * An exact value that is matched as a whole and can be sorted on.
     */
    KEYWORD,
    /**
     * Text indexed for prefix (search-as-you-type) matching.
     */
    AUTOCOMPLETE
}
