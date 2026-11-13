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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.query.builder.DelegatingQueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Constants shared by the compile-time query builders and the runtime for full-text search.
 *
 * @since 5.3.0
 */
@Internal
public final class TextSearchSupport {

    /**
     * The repository method keyword, as in {@code findByTitleSearch(String)}.
     */
    public static final String KEYWORD = "Search";

    /**
     * Synthetic function name used in the criteria tree for the relevance score of a text match.
     * The arguments are the matched field expressions, then the query parameter, then a literal
     * holding the {@link MatchMode} name.
     */
    public static final String SCORE_FUNCTION = "mn_search_score";

    /**
     * The result column alias the relevance score is projected to. Shared with vector search so the
     * same results mapper reads both.
     */
    public static final String SCORE_ALIAS = "mn_score";

    /**
     * The default language when an entity declares no {@code @Searchable(language)}.
     */
    public static final String DEFAULT_LANGUAGE = "english";

    /**
     * Synthetic function name used in the criteria tree for the highlighted fragment of one field.
     * The arguments are the field expression, the query parameter and a literal holding the
     * {@link MatchMode} name; the function renders a {@link String}.
     */
    public static final String HIGHLIGHT_FUNCTION = "mn_search_highlight";

    /**
     * The prefix of the result column alias a highlighted fragment is projected to, followed by the
     * property name.
     */
    public static final String HIGHLIGHT_ALIAS_PREFIX = "mn_hl_";

    /**
     * The tag opening a highlighted term, the same on every store.
     */
    public static final String HIGHLIGHT_START_TAG = "<em>";

    /**
     * The tag closing a highlighted term.
     */
    public static final String HIGHLIGHT_END_TAG = "</em>";

    private static final String HIGHLIGHT_ALIAS_CLAUSE = " AS " + HIGHLIGHT_ALIAS_PREFIX;

    private static final List<MatchMode> KEYWORDS_LONGEST_FIRST = Arrays.stream(MatchMode.values())
        .sorted(Comparator.comparingInt((MatchMode m) -> m.keyword().length()).reversed())
        .toList();

    private TextSearchSupport() {
    }

    /**
     * The properties of an entity annotated with {@code @SearchField}.
     *
     * @param entity The entity
     * @return The search field properties, in declaration order
     */
    public static List<PersistentProperty> searchFields(PersistentEntity entity) {
        List<PersistentProperty> fields = new ArrayList<>();
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property.getAnnotationMetadata().hasAnnotation(SearchField.class)) {
                fields.add(property);
            }
        }
        return fields;
    }

    /**
     * The result column alias of the highlighted fragment of a property.
     *
     * @param propertyName The property name
     * @return The alias
     */
    public static String highlightAlias(String propertyName) {
        return HIGHLIGHT_ALIAS_PREFIX + propertyName;
    }

    /**
     * Finds the highlighted fragments a rendered query projects, so the result mapper knows which
     * columns hold fragments and for which properties.
     *
     * <p>Only the {@code @SearchField} properties of the entity are candidates, and a property counts as
     * highlighted only when the query projects it under the exact alias the query builder renders,
     * {@code AS mn_hl_<property>} ending at an identifier boundary. A column, alias or literal that merely
     * starts with {@link #HIGHLIGHT_ALIAS_PREFIX} and names no search field is ignored.</p>
     *
     * @param query The rendered query
     * @param entity The queried entity
     * @return The property names, in projection order
     */
    public static List<String> highlightedProperties(String query, PersistentEntity entity) {
        if (!query.contains(HIGHLIGHT_ALIAS_CLAUSE)) {
            return List.of();
        }
        List<Map.Entry<Integer, String>> found = new ArrayList<>();
        for (PersistentProperty property : searchFields(entity)) {
            int position = aliasPosition(query, highlightAlias(property.getName()));
            if (position >= 0) {
                found.add(Map.entry(position, property.getName()));
            }
        }
        found.sort(Map.Entry.comparingByKey());
        return found.stream().map(Map.Entry::getValue).toList();
    }

    private static int aliasPosition(String query, String alias) {
        String clause = " AS " + alias;
        int from = 0;
        while (true) {
            int index = query.indexOf(clause, from);
            if (index < 0) {
                return -1;
            }
            int end = index + clause.length();
            if (end == query.length() || !Character.isJavaIdentifierPart(query.charAt(end))) {
                return index;
            }
            from = end;
        }
    }

    /**
     * Resolves the search language of an entity.
     *
     * @param entity The entity
     * @return The declared {@code @Searchable(language)}, or {@link #DEFAULT_LANGUAGE}
     */
    public static String resolveLanguage(PersistentEntity entity) {
        return entity.getAnnotationMetadata().stringValue(Searchable.class, Searchable.MEMBER_LANGUAGE)
            .filter(l -> !l.isEmpty())
            .orElse(DEFAULT_LANGUAGE);
    }

    /**
     * Wraps a parameter binding so the bound value is converted with the given converter.
     *
     * @param binding The original binding
     * @param converterClassName The converter class name
     * @return The wrapped binding
     */
    public static QueryParameterBinding withConverter(QueryParameterBinding binding, String converterClassName) {
        return new DelegatingQueryParameterBinding(binding) {
            @Override
            public String getConverterClassName() {
                return converterClassName;
            }
        };
    }

    /**
     * Splits a method predicate part such as {@code TitleSearchAll} into the property name and match mode.
     *
     * @param predicatePart The predicate part
     * @return The property name (possibly empty) and mode, or {@code null} if the part is not a search keyword
     */
    public static @Nullable SearchKeyword parseKeyword(String predicatePart) {
        for (MatchMode mode : KEYWORDS_LONGEST_FIRST) {
            String keyword = mode.keyword();
            if (predicatePart.endsWith(keyword)) {
                return new SearchKeyword(predicatePart.substring(0, predicatePart.length() - keyword.length()), mode);
            }
        }
        return null;
    }

    /**
     * A parsed search keyword.
     *
     * @param propertyName The property name before the keyword, empty for all search fields
     * @param mode The match mode of the keyword
     */
    public record SearchKeyword(String propertyName, MatchMode mode) {
    }
}
