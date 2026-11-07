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
import io.micronaut.data.annotation.search.Language;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.model.query.builder.DelegatingQueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.PersistentEntity;
import jakarta.persistence.criteria.Expression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
import io.micronaut.data.model.PersistentProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constants shared by the compile-time query builders and the runtime for full-text search.
 *
 * @since 5.4.0
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
     * Synthetic function name used in the criteria tree for the fused score of a hybrid search a store
     * computes natively. The arguments are the {@link #SCORE_FUNCTION} expression, the vector score
     * expression, and literals holding the text weight, the vector weight and the candidates of each ranking.
     *
     * @since 5.4.0
     */
    public static final String HYBRID_SCORE_FUNCTION = "mn_hybrid_score";

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

    private static final java.util.regex.Pattern LANGUAGE_NAME = java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final Pattern HIGHLIGHT_ALIAS = Pattern.compile("\\b" + HIGHLIGHT_ALIAS_PREFIX + "([A-Za-z0-9_]+)");

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
     * Resolves the search language of an entity.
     *
     * @param entity The entity
     * @return The declared {@code @Searchable(language)}, or {@link #DEFAULT_LANGUAGE}
     */
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
     * Finds the highlight aliases a rendered query projects, so the result mapper knows which
     * columns hold fragments and for which properties.
     *
     * @param query The rendered query
     * @return The property names, in projection order
     */
    public static List<String> highlightedProperties(String query) {
        List<String> properties = new ArrayList<>();
        Matcher matcher = HIGHLIGHT_ALIAS.matcher(query);
        while (matcher.find()) {
            String property = matcher.group(1);
            if (!properties.contains(property)) {
                properties.add(property);
            }
        }
        return properties;
    }

    /**
     * The property annotated {@code @Language}, holding the per-row language.
     *
     * @param entity The entity
     * @return The property, or {@code null}
     */
    @Nullable
    public static PersistentProperty languageProperty(PersistentEntity entity) {
        for (PersistentProperty property : entity.getPersistentProperties()) {
            if (property.getAnnotationMetadata().hasAnnotation(Language.class)) {
                return property;
            }
        }
        return null;
    }

    /**
     * @param property A property
     * @return Whether it is a {@code @SearchField(highlightable = true)}
     */
    public static boolean isHighlightable(PersistentProperty property) {
        return property.getAnnotationMetadata().booleanValue(SearchField.class, SearchField.MEMBER_HIGHLIGHTABLE).orElse(false);
    }

    /**
     * Reads the arguments of the synthetic {@link #SCORE_FUNCTION}: the field expressions, the query
     * parameter, a literal holding the {@link MatchMode} name and, when set, a literal holding the
     * {@link TextMatchOptions}.
     *
     * @param expressions The function arguments
     * @return The arguments
     * @since 5.4.0
     */
    public static ScoreArguments scoreArguments(List<? extends Expression<?>> expressions) {
        int end = expressions.size();
        TextMatchOptions options = TextMatchOptions.NONE;
        if (end > 0 && expressions.get(end - 1) instanceof LiteralExpression<?> literal && literal.getValue() instanceof TextMatchOptions textMatchOptions) {
            options = textMatchOptions;
            end--;
        }
        if (end < 3) {
            throw new IllegalStateException("Search score expects fields, a query and a match mode");
        }
        Expression<?> modeExpression = expressions.get(end - 1);
        MatchMode mode = modeExpression instanceof LiteralExpression<?> literal && literal.getValue() != null
            ? MatchMode.valueOf(literal.getValue().toString())
            : MatchMode.ANY;
        @SuppressWarnings("unchecked")
        Expression<String> query = (Expression<String>) expressions.get(end - 2);
        return new ScoreArguments(List.copyOf(expressions.subList(0, end - 2)), query, mode, options);
    }

    public static String resolveLanguage(PersistentEntity entity) {
        return requireLanguageName(entity.getAnnotationMetadata().stringValue(Searchable.class, Searchable.MEMBER_LANGUAGE)
            .filter(l -> !l.isEmpty())
            .orElse(DEFAULT_LANGUAGE));
    }

    /**
     * Checks that a language is a plain configuration name, as the SQL dialects render it inside the
     * statement rather than binding it: letters, digits and underscores only.
     *
     * @param language The language from {@code @Searchable(language)} or {@code SearchQuery.language}
     * @return The language
     * @throws IllegalArgumentException When the language holds any other character
     */
    public static String requireLanguageName(String language) {
        if (!LANGUAGE_NAME.matcher(language).matches()) {
            throw new IllegalArgumentException("A search language must be a text search configuration name of letters, digits and underscores");
        }
        return language;
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

    /**
     * The arguments of the synthetic {@link #SCORE_FUNCTION}.
     *
     * @param fields The field expressions
     * @param query The query expression
     * @param mode The match mode
     * @param options The boosts and fuzziness
     * @since 5.4.0
     */
    public record ScoreArguments(List<Expression<?>> fields, Expression<String> query, MatchMode mode, TextMatchOptions options) {
    }
}
