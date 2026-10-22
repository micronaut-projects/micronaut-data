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
package io.micronaut.data.search.document.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.TextSearchQueryConverter;
import io.micronaut.data.model.search.MatchMode;
import io.micronaut.data.model.search.SearchQueryParser;
import io.micronaut.data.model.search.SearchTerm;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.StringJoiner;

/**
 * Converts a search string in the portable grammar into the Lucene {@code simple_query_string} syntax
 * shared by Elasticsearch and OpenSearch: {@code "quoted phrase"}, {@code -negated}, {@code |} for OR,
 * {@code prefix*} and {@code term~1}. Special characters in terms are escaped so user input is matched
 * literally.
 *
 * @since 5.4.0
 */
@Internal
public abstract class SimpleQueryStringConverter implements TextSearchQueryConverter {

    private static final String SPECIALS = "+|-\"*()~\\";

    /**
     * @param mode The match mode
     * @return The class name of the converter for the mode
     */
    public static String converterFor(MatchMode mode) {
        return switch (mode) {
            case ANY, ALL -> Terms.class.getName();
            case PHRASE -> Phrase.class.getName();
            case PREFIX -> Prefix.class.getName();
            case FUZZY -> Fuzzy.class.getName();
        };
    }

    /**
     * Renders a search string for the mode.
     *
     * @param query The search string
     * @param mode The match mode
     * @return The {@code simple_query_string} query
     */
    public static String render(@Nullable String query, MatchMode mode) {
        if (query == null) {
            return "";
        }
        if (mode == MatchMode.PHRASE) {
            String phrase = query.replace("\"", "").trim();
            return phrase.isEmpty() ? "" : "\"" + escape(phrase) + "\"";
        }
        List<List<SearchTerm>> groups = SearchQueryParser.parse(query);
        StringJoiner disjunction = new StringJoiner(" | ");
        for (List<SearchTerm> group : groups) {
            StringJoiner terms = new StringJoiner(" ");
            for (SearchTerm term : group) {
                String rendered;
                if (term.phrase()) {
                    rendered = "\"" + escape(term.text()) + "\"";
                } else {
                    rendered = escape(term.text());
                    if (mode == MatchMode.PREFIX) {
                        rendered += "*";
                    } else if (mode == MatchMode.FUZZY) {
                        rendered += "~1";
                    }
                }
                terms.add(term.negated() ? "-" + rendered : rendered);
            }
            if (terms.length() > 0) {
                disjunction.add(groups.size() > 1 ? "(" + terms + ")" : terms.toString());
            }
        }
        return disjunction.toString();
    }

    private static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (SPECIALS.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    @Nullable
    public String convertToPersistedValue(@Nullable String entityValue, ConversionContext context) {
        return render(entityValue, mode());
    }

    @Override
    @Nullable
    public String convertToEntityValue(@Nullable String persistedValue, ConversionContext context) {
        return persistedValue;
    }

    /**
     * @return The match mode this converter renders
     */
    protected abstract MatchMode mode();

    /**
     * Any-term or all-terms converter; the operator is set on the query.
     */
    @Singleton
    public static final class Terms extends SimpleQueryStringConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.ANY;
        }
    }

    /**
     * Phrase converter.
     */
    @Singleton
    public static final class Phrase extends SimpleQueryStringConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.PHRASE;
        }
    }

    /**
     * Prefix converter.
     */
    @Singleton
    public static final class Prefix extends SimpleQueryStringConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.PREFIX;
        }
    }

    /**
     * Fuzzy converter.
     */
    @Singleton
    public static final class Fuzzy extends SimpleQueryStringConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.FUZZY;
        }
    }
}
