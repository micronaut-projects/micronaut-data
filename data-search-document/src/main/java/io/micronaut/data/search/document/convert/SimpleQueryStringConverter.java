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

import java.util.ArrayList;
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

    /**
     * The most edits Lucene fuzzy matching allows.
     */
    public static final int MAX_FUZZINESS = 2;
    private static final String SPECIALS = "+|-\"*()~\\";

    /**
     * @param mode The match mode
     * @return The class name of the converter for the mode
     */
    public static String converterFor(MatchMode mode) {
        return converterFor(mode, 0);
    }

    /**
     * @param mode The match mode
     * @param fuzziness The maximum edit distance of a fuzzy match, {@code 0} for the default of one edit
     * @return The class name of the converter for the mode
     */
    public static String converterFor(MatchMode mode, int fuzziness) {
        if (mode == MatchMode.FUZZY && fuzziness >= MAX_FUZZINESS) {
            return Fuzzy2.class.getName();
        }
        return switch (mode) {
            case ANY -> Any.class.getName();
            case ALL -> All.class.getName();
            case PHRASE -> Phrase.class.getName();
            case PREFIX -> Prefix.class.getName();
            case FUZZY -> Fuzzy.class.getName();
            case NATIVE -> Native.class.getName();
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
        return render(query, mode, 1);
    }

    /**
     * Renders a search string for the mode.
     *
     * @param query The search string
     * @param mode The match mode
     * @param fuzziness The maximum edit distance of a fuzzy match
     * @return The {@code simple_query_string} query
     */
    public static String render(@Nullable String query, MatchMode mode, int fuzziness) {
        if (query == null) {
            return "";
        }
        if (mode == MatchMode.NATIVE) {
            // the caller wrote query_string syntax
            return query;
        }
        if (mode == MatchMode.PHRASE) {
            String phrase = query.replace("\"", "").trim();
            return phrase.isEmpty() ? "" : "\"" + escape(phrase) + "\"";
        }
        List<List<SearchTerm>> groups = SearchQueryParser.parse(query);
        // The query runs with default_operator "and", so any-term matching is spelled out with "|"
        // and a negated term always excludes, whatever the mode
        boolean anyTerm = mode == MatchMode.ANY || mode == MatchMode.FUZZY;
        StringJoiner disjunction = new StringJoiner(" | ");
        for (List<SearchTerm> group : groups) {
            List<String> positive = new ArrayList<>(group.size());
            List<String> negative = new ArrayList<>();
            for (SearchTerm term : group) {
                String rendered;
                if (term.phrase()) {
                    rendered = "\"" + escape(term.text()) + "\"";
                } else {
                    rendered = escape(term.text());
                    if (mode == MatchMode.PREFIX) {
                        rendered += "*";
                    } else if (mode == MatchMode.FUZZY) {
                        rendered += "~" + fuzziness;
                    }
                }
                (term.negated() ? negative : positive).add(rendered);
            }
            if (positive.isEmpty() && negative.isEmpty()) {
                continue;
            }
            String terms = String.join(anyTerm ? " | " : " ", positive);
            if (anyTerm && positive.size() > 1 && !negative.isEmpty()) {
                terms = "(" + terms + ")";
            }
            if (!negative.isEmpty()) {
                terms = (terms.isEmpty() ? "" : terms + " ") + "-" + String.join(" -", negative);
            }
            disjunction.add(groups.size() > 1 ? "(" + terms + ")" : terms);
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
        return render(entityValue, mode(), fuzziness());
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
     * @return The maximum edit distance this converter renders for a fuzzy match
     */
    protected int fuzziness() {
        return 1;
    }

    /**
     * Any-term converter.
     */
    @Singleton
    public static final class Any extends SimpleQueryStringConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.ANY;
        }
    }

    /**
     * All-terms converter.
     */
    @Singleton
    public static final class All extends SimpleQueryStringConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.ALL;
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

    /**
     * {@link MatchMode#NATIVE} converter: the string is {@code query_string} syntax already.
     */
    @Singleton
    public static final class Native extends SimpleQueryStringConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.NATIVE;
        }
    }

    /**
     * {@link MatchMode#FUZZY} converter with two edits.
     */
    @Singleton
    public static final class Fuzzy2 extends SimpleQueryStringConverter {
        @Override
        protected MatchMode mode() {
            return MatchMode.FUZZY;
        }

        @Override
        protected int fuzziness() {
            return MAX_FUZZINESS;
        }
    }
}
