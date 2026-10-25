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
package io.micronaut.data.search.document.init;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchFieldType;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.search.TextSearchSupport;
import io.micronaut.data.search.document.query.SearchJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the index mapping of a searchable entity: {@code text} fields with the entity language as
 * analyzer and a {@code keyword} sub-field for exact matches and sorting, {@code keyword} fields for
 * {@link SearchFieldType#KEYWORD}, and {@code search_as_you_type} for {@link SearchFieldType#AUTOCOMPLETE}.
 * Every other property is mapped dynamically by the store.
 *
 * @since 5.4.0
 */
@Internal
public final class SearchIndexDefinitions {

    private static final String KEYWORD = "keyword";
    private static final String FIELDS = "fields";
    private static final Map<String, Object> KEYWORD_SUB_FIELD = Map.of("keyword", Map.of("type", "keyword", "ignore_above", 1024));
    private static final String ANALYZER = "analyzer";
    private static final String TYPE = "type";

    private SearchIndexDefinitions() {
    }

    /**
     * @param entity The entity
     * @return The create-index request body JSON
     */
    public static String mappingFor(PersistentEntity entity) {
        String analyzer = analyzerFor(TextSearchSupport.resolveLanguage(entity));
        Map<String, Object> properties = new LinkedHashMap<>();
        for (PersistentProperty property : TextSearchSupport.searchFields(entity)) {
            SearchFieldType type = property.getAnnotationMetadata().enumValue(SearchField.class, SearchField.MEMBER_TYPE, SearchFieldType.class).orElse(SearchFieldType.TEXT);
            String declaredAnalyzer = property.getAnnotationMetadata().stringValue(SearchField.class, ANALYZER).filter(a -> !a.isEmpty()).orElse(analyzer);
            Map<String, Object> field = new LinkedHashMap<>();
            switch (type) {
                case KEYWORD -> field.put(TYPE, KEYWORD);
                case AUTOCOMPLETE -> {
                    field.put(TYPE, "search_as_you_type");
                    field.put(ANALYZER, declaredAnalyzer);
                    // exact matches and sorts use the keyword sub-field, as for an analysed text field
                    field.put(FIELDS, KEYWORD_SUB_FIELD);
                }
                default -> {
                    field.put(TYPE, "text");
                    field.put(ANALYZER, declaredAnalyzer);
                    field.put(FIELDS, KEYWORD_SUB_FIELD);
                }
            }
            properties.put(property.getName(), field);
        }
        // The other string properties are compared exactly by the derived queries, so they are
        // keywords rather than the analyzed text a dynamic mapping would give them; the id is one of
        // them, as the cursored pages sort on it as a tiebreaker
        List<PersistentProperty> others = new ArrayList<>(entity.getPersistentProperties());
        if (entity.getIdentity() != null) {
            others.add(entity.getIdentity());
        }
        for (PersistentProperty property : others) {
            if (!properties.containsKey(property.getName()) && !(property instanceof Association) && isKeyword(property)) {
                properties.put(property.getName(), Map.of(TYPE, KEYWORD));
            }
        }
        return SearchJson.write(Map.of("mappings", Map.of("properties", properties)));
    }

    private static boolean isKeyword(PersistentProperty property) {
        DataType dataType = property.getDataType();
        return dataType == DataType.STRING || dataType == DataType.UUID || dataType == DataType.CHARACTER || property.isEnum();
    }

    /**
     * Maps a language name to the built-in language analyzer of the store.
     *
     * @param language The language
     * @return The analyzer name
     */
    public static String analyzerFor(String language) {
        return switch (language) {
            case "simple", "none" -> "standard";
            default -> language;
        };
    }
}
