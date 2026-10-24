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
     * The default language when an entity declares no {@code @Searchable(language)}.
     */
    public static final String DEFAULT_LANGUAGE = "english";

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
