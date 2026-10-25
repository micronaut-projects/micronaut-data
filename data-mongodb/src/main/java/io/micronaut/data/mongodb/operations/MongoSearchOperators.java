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
package io.micronaut.data.mongodb.operations;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.search.MatchMode;
import io.micronaut.data.model.search.SearchQueryParser;
import io.micronaut.data.model.search.SearchTerm;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the portable search string grammar as MongoDB Search ({@code $search}) operators:
 * every term becomes a {@code text}, {@code phrase} or {@code wildcard} operator over the
 * searched paths, combined with {@code compound}. Terms are {@code should} clauses for
 * {@link MatchMode#ANY} and {@code must} clauses for {@link MatchMode#ALL}, negated terms are
 * {@code mustNot} clauses, and {@code OR} groups are {@code should} clauses of the outer compound.
 * Paths with an index weight are boosted with the {@code score} option.
 *
 * @author Graeme Rocher
 * @since 5.3
 */
@Internal
public final class MongoSearchOperators {

    private static final String COMPOUND = "compound";
    private static final String MUST = "must";
    private static final String MUST_NOT = "mustNot";
    private static final String SHOULD = "should";
    private static final String MINIMUM_SHOULD_MATCH = "minimumShouldMatch";
    private static final String PATH = "path";
    private static final String QUERY = "query";

    private MongoSearchOperators() {
    }

    /**
     * Renders a search string.
     *
     * @param query The search string
     * @param mode The match mode
     * @param paths The searched field paths
     * @param boosts The boost of the paths with a weight other than 1
     * @return The operator document
     */
    public static BsonDocument render(@Nullable String query, MatchMode mode, List<String> paths, Map<String, Number> boosts) {
        if (query == null || query.isBlank()) {
            return matchNothing(paths);
        }
        if (mode == MatchMode.PHRASE) {
            String phrase = query.replace("\"", "").trim();
            return phrase.isEmpty() ? matchNothing(paths) : termOperator(new SearchTerm(phrase, true, false), mode, paths, boosts);
        }
        List<BsonValue> rendered = new ArrayList<>();
        for (List<SearchTerm> group : SearchQueryParser.parse(query)) {
            BsonDocument clause = renderGroup(group, mode, paths, boosts);
            if (clause != null) {
                rendered.add(clause);
            }
        }
        return joinGroups(rendered, paths);
    }

    /** One group stands alone; several are alternatives in a compound {@code should}. */
    private static BsonDocument joinGroups(List<BsonValue> rendered, List<String> paths) {
        if (rendered.isEmpty()) {
            return matchNothing(paths);
        }
        if (rendered.size() == 1) {
            return rendered.get(0).asDocument();
        }
        return new BsonDocument(COMPOUND, new BsonDocument(SHOULD, new BsonArray(rendered)).append(MINIMUM_SHOULD_MATCH, new BsonInt32(1)));
    }

    @Nullable
    private static BsonDocument renderGroup(List<SearchTerm> group, MatchMode mode, List<String> paths, Map<String, Number> boosts) {
        BsonArray positive = new BsonArray();
        BsonArray negative = new BsonArray();
        for (SearchTerm term : group) {
            if (term.text().isBlank()) {
                continue;
            }
            (term.negated() ? negative : positive).add(termOperator(term, mode, paths, boosts));
        }
        if (positive.isEmpty() && negative.isEmpty()) {
            return null;
        }
        if (positive.size() == 1 && negative.isEmpty()) {
            return positive.get(0).asDocument();
        }
        BsonDocument compound = new BsonDocument();
        if (!positive.isEmpty()) {
            if (mode == MatchMode.ALL) {
                compound.append(MUST, positive);
            } else {
                compound.append(SHOULD, positive).append(MINIMUM_SHOULD_MATCH, new BsonInt32(1));
            }
        }
        if (!negative.isEmpty()) {
            compound.append(MUST_NOT, negative);
            if (positive.isEmpty()) {
                // A purely negative query still has to select something to exclude from
                compound.append(MUST, new BsonArray(List.of(exists(paths.get(0)))));
            }
        }
        return new BsonDocument(COMPOUND, compound);
    }

    /**
     * Renders one term over the paths, one operator per boost so each can carry its score.
     */
    private static BsonDocument termOperator(SearchTerm term, MatchMode mode, List<String> paths, Map<String, Number> boosts) {
        Map<Number, List<String>> pathsByBoost = new LinkedHashMap<>();
        for (String path : paths) {
            pathsByBoost.computeIfAbsent(boosts.getOrDefault(path, 1), k -> new ArrayList<>()).add(path);
        }
        if (pathsByBoost.size() == 1) {
            Map.Entry<Number, List<String>> only = pathsByBoost.entrySet().iterator().next();
            return operator(term, mode, only.getValue(), only.getKey());
        }
        BsonArray should = new BsonArray();
        for (Map.Entry<Number, List<String>> entry : pathsByBoost.entrySet()) {
            should.add(operator(term, mode, entry.getValue(), entry.getKey()));
        }
        return new BsonDocument(COMPOUND, new BsonDocument(SHOULD, should).append(MINIMUM_SHOULD_MATCH, new BsonInt32(1)));
    }

    private static BsonDocument operator(SearchTerm term, MatchMode mode, List<String> paths, Number boost) {
        BsonValue path = paths.size() == 1 ? new BsonString(paths.get(0)) : new BsonArray(paths.stream().map(p -> (BsonValue) new BsonString(p)).toList());
        BsonDocument options = new BsonDocument();
        String name;
        if (term.phrase()) {
            name = "phrase";
            options.append(QUERY, new BsonString(term.text())).append(PATH, path);
        } else if (mode == MatchMode.PREFIX) {
            name = "wildcard";
            options.append(QUERY, new BsonString(escapeWildcard(term.text().toLowerCase(Locale.ROOT)) + "*"))
                .append(PATH, path)
                .append("allowAnalyzedField", BsonBoolean.TRUE);
        } else {
            name = "text";
            options.append(QUERY, new BsonString(term.text())).append(PATH, path);
            if (mode == MatchMode.FUZZY) {
                options.append("fuzzy", new BsonDocument("maxEdits", new BsonInt32(1)));
            }
        }
        if (boost.doubleValue() != 1d) {
            options.append("score", new BsonDocument("boost", new BsonDocument("value", new BsonDouble(boost.doubleValue()))));
        }
        return new BsonDocument(name, options);
    }

    private static String escapeWildcard(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '*' || c == '?' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static BsonDocument exists(String path) {
        return new BsonDocument("exists", new BsonDocument(PATH, new BsonString(path)));
    }

    /**
     * @param paths The searched paths
     * @return An operator that matches no document, for an empty search string: a field cannot both
     * exist and not exist, and only the indexed fields can be tested
     */
    private static BsonDocument matchNothing(List<String> paths) {
        BsonDocument exists = exists(paths.get(0));
        return new BsonDocument(COMPOUND, new BsonDocument(MUST, new BsonArray(List.of(exists)))
            .append(MUST_NOT, new BsonArray(List.of(exists.clone()))));
    }
}
