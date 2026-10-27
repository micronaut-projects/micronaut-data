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
 * One term of a parsed search string.
 *
 * @param text The term text, with quotes and the negation marker removed
 * @param phrase Whether the term was quoted and must match as a phrase
 * @param negated Whether the term was prefixed with {@code -} and must not match
 * @since 5.4.0
 */
@Experimental
public record SearchTerm(String text, boolean phrase, boolean negated) {

    /**
     * @param text The term text
     * @return A plain positive term
     */
    public static SearchTerm of(String text) {
        return new SearchTerm(text, false, false);
    }
}
