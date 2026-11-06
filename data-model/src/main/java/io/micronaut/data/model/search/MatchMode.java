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
import org.jspecify.annotations.Nullable;

/**
 * How the terms of a full-text query are matched against a field.
 *
 * @since 5.4.0
 */
@Experimental
public enum MatchMode {
    /**
     * Any of the terms must match. The default.
     */
    ANY,
    /**
     * All of the terms must match.
     */
    ALL,
    /**
     * The terms must appear adjacent and in order.
     */
    PHRASE,
    /**
     * Every term is matched as a prefix, for search-as-you-type.
     */
    PREFIX,
    /**
     * Terms match within an edit distance, where the backend supports it.
     */
    FUZZY;

    /**
     * Resolves the match mode of a method keyword such as {@code SearchAll} or {@code SearchPhrase}.
     *
     * @param keyword The keyword, with or without the {@code Search} prefix
     * @return The match mode, or {@code null} if the keyword is not a search keyword
     */
    public static @Nullable MatchMode fromKeyword(String keyword) {
        String suffix = keyword.startsWith(TextSearchSupport.KEYWORD) ? keyword.substring(TextSearchSupport.KEYWORD.length()) : keyword;
        return switch (suffix) {
            case "" -> ANY;
            case "All" -> ALL;
            case "Phrase" -> PHRASE;
            case "Prefix" -> PREFIX;
            case "Fuzzy" -> FUZZY;
            default -> null;
        };
    }

    /**
     * @return The method keyword for this mode, for example {@code SearchAll}
     */
    public String keyword() {
        return switch (this) {
            case ANY -> TextSearchSupport.KEYWORD;
            case ALL -> TextSearchSupport.KEYWORD + "All";
            case PHRASE -> TextSearchSupport.KEYWORD + "Phrase";
            case PREFIX -> TextSearchSupport.KEYWORD + "Prefix";
            case FUZZY -> TextSearchSupport.KEYWORD + "Fuzzy";
        };
    }
}
