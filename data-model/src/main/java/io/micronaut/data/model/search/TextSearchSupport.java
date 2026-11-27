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
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import jakarta.persistence.criteria.Expression;
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression;
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
     * Synthetic function name used in the criteria tree for the fused score of a hybrid search a store
     * computes natively. The arguments are the {@link #SCORE_FUNCTION} expression, the vector score
     * expression, and literals holding the text weight, the vector weight and the candidates of each ranking.
     *
     * @since 5.3.0
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
     * @since 5.3.0
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

    /**
     * Checks the options of a text match against its fields and mode: every boost must name the property path
     * of one of the matched fields, and an edit distance needs the {@link MatchMode#FUZZY} mode.
     *
     * @param fields The matched field expressions
     * @param mode The match mode
     * @param options The options
     * @throws IllegalArgumentException When a boost names no matched field, or an edit distance is set with another mode
     * @since 5.3.0
     */
    public static void validateOptions(List<? extends Expression<?>> fields, MatchMode mode, TextMatchOptions options) {
        if (options.fuzziness() > 0 && mode != MatchMode.FUZZY) {
            throw new IllegalArgumentException("An edit distance applies only to a FUZZY match, not to " + mode + ": " + options.fuzziness());
        }
        if (options.boosts().isEmpty()) {
            return;
        }
        List<String> paths = new ArrayList<>(fields.size());
        for (Expression<?> field : fields) {
            if (field instanceof PersistentPropertyPath<?> propertyPath) {
                paths.add(propertyPath.getPathAsString());
            }
        }
        for (String boosted : options.boosts().keySet()) {
            if (!paths.contains(boosted)) {
                throw new IllegalArgumentException("The boosted property '" + boosted + "' is not one of the matched fields " + paths);
            }
        }
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

    /**
     * The arguments of the synthetic {@link #SCORE_FUNCTION}.
     *
     * @param fields The field expressions
     * @param query The query expression
     * @param mode The match mode
     * @param options The boosts and fuzziness
     * @since 5.3.0
     */
    public record ScoreArguments(List<Expression<?>> fields, Expression<String> query, MatchMode mode, TextMatchOptions options) {
    }
}
