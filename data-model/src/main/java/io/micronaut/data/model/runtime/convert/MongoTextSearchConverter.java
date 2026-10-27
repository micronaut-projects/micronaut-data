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
package io.micronaut.data.model.runtime.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.search.MatchMode;
import io.micronaut.data.model.search.SearchQueryParser;
import io.micronaut.data.model.search.SearchTerm;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.StringJoiner;

/**
 * Converts a search string in the portable grammar into the {@code $search} string of a MongoDB
 * {@code $text} query for a {@link MatchMode}.
 *
 * <p>MongoDB matches any term by default, requires every quoted phrase, and excludes terms prefixed
 * with {@code -}; a term that contains a quote is quoted as a phrase. {@link MatchMode#ALL} is rendered
 * by quoting every term, which MongoDB treats as a required phrase. {@code OR} groups are flattened
 * for {@link MatchMode#ANY} and rejected for the other modes.</p>
 *
 * @since 5.4.0
 */
@Internal
public abstract class MongoTextSearchConverter implements TextSearchQueryConverter {

    private final MatchMode mode;

    /**
     * @param mode The match mode this converter renders
     */
    protected MongoTextSearchConverter(MatchMode mode) {
        this.mode = mode;
    }

    /**
     * @param mode The match mode
     * @return The class name of the converter for the mode
     */
    public static String converterFor(MatchMode mode) {
        return switch (mode) {
            case ANY -> Any.class.getName();
            case ALL -> All.class.getName();
            case PHRASE -> Phrase.class.getName();
            case NATIVE -> Native.class.getName();
            case PREFIX, FUZZY -> throw new IllegalArgumentException("MongoDB $text does not support " + mode + " matching");
        };
    }

    /**
     * Renders a search string as a {@code $text} search string for the mode.
     *
     * @param query The search string
     * @param mode The match mode
     * @return The {@code $search} value
     */
    public static String render(@Nullable String query, MatchMode mode) {
        if (query == null) {
            return "";
        }
        if (mode == MatchMode.NATIVE) {
            // the caller wrote $text search syntax
            return query;
        }
        if (mode == MatchMode.PHRASE) {
            String phrase = query.replace("\"", "").trim();
            return phrase.isEmpty() ? "" : quote(phrase);
        }
        List<List<SearchTerm>> groups = SearchQueryParser.parse(query);
        if (mode == MatchMode.ALL && groups.size() > 1) {
            throw new IllegalArgumentException("MongoDB $text cannot combine OR with a SearchAll query");
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (List<SearchTerm> group : groups) {
            for (SearchTerm term : group) {
                joiner.add(renderTerm(term, mode));
            }
        }
        return joiner.toString();
    }

    private static String renderTerm(SearchTerm term, MatchMode mode) {
        boolean quoted = term.phrase() || mode == MatchMode.ALL || term.text().indexOf('"') >= 0;
        String rendered = quoted ? quote(term.text()) : term.text();
        return term.negated() ? "-" + rendered : rendered;
    }

    private static String quote(String text) {
        return "\"" + text.replace("\"", "") + "\"";
    }

    @Override
    @Nullable
    public String convertToPersistedValue(@Nullable String entityValue, ConversionContext context) {
        return render(entityValue, mode);
    }

    @Override
    @Nullable
    public String convertToEntityValue(@Nullable String persistedValue, ConversionContext context) {
        return persistedValue;
    }

    /**
     * {@link MatchMode#ANY} converter.
     */
    @Singleton
    public static final class Any extends MongoTextSearchConverter {
        public Any() {
            super(MatchMode.ANY);
        }
    }

    /**
     * {@link MatchMode#ALL} converter.
     */
    @Singleton
    public static final class All extends MongoTextSearchConverter {
        public All() {
            super(MatchMode.ALL);
        }
    }

    /**
     * {@link MatchMode#PHRASE} converter.
     */
    @Singleton
    public static final class Phrase extends MongoTextSearchConverter {
        public Phrase() {
            super(MatchMode.PHRASE);
        }
    }

    /**
     * {@link MatchMode#NATIVE} converter: the string is {@code $text} search syntax already.
     */
    @Singleton
    public static final class Native extends MongoTextSearchConverter {
        public Native() {
            super(MatchMode.NATIVE);
        }
    }
}
