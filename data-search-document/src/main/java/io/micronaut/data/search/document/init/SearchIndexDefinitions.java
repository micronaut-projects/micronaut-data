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
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.search.TextSearchSupport;
import io.micronaut.data.search.document.query.SearchJson;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.search.document.runtime.SearchTransport;
import io.micronaut.data.model.vector.Vector;

import java.util.LinkedHashMap;
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

    private SearchIndexDefinitions() {
    }

    /**
     * @param entity The entity
     * @return The create-index request body JSON
     */
    public static String mappingFor(PersistentEntity entity) {
        return mappingFor(entity, null);
    }

    /**
     * The index body of an entity: the search field mappings, the vector field mappings and the
     * index settings of the store.
     *
     * @param entity The entity
     * @param transport The transport creating the index, for store-specific vector mappings and settings
     * @return The create-index body as JSON
     */
    public static String mappingFor(PersistentEntity entity, @Nullable SearchTransport transport) {
        String analyzer = analyzerFor(TextSearchSupport.resolveLanguage(entity));
        Map<String, Object> properties = new LinkedHashMap<>();
        if (transport != null) {
            for (PersistentProperty property : entity.getPersistentProperties()) {
                if (property.isAssignable(Vector.class)) {
                    Map<String, Object> mapping = transport.vectorFieldMapping(vectorDimensions(property));
                    if (mapping != null) {
                        properties.put(property.getName(), mapping);
                    }
                }
            }
        }
        for (PersistentProperty property : TextSearchSupport.searchFields(entity)) {
            SearchFieldType type = property.getAnnotationMetadata().enumValue(SearchField.class, SearchField.MEMBER_TYPE, SearchFieldType.class).orElse(SearchFieldType.TEXT);
            String declaredAnalyzer = property.getAnnotationMetadata().stringValue(SearchField.class, "analyzer").filter(a -> !a.isEmpty()).orElse(analyzer);
            Map<String, Object> field = new LinkedHashMap<>();
            switch (type) {
                case KEYWORD -> field.put("type", "keyword");
                case AUTOCOMPLETE -> {
                    field.put("type", "search_as_you_type");
                    field.put("analyzer", declaredAnalyzer);
                }
                default -> {
                    field.put("type", "text");
                    field.put("analyzer", declaredAnalyzer);
                    field.put("fields", Map.of("keyword", Map.of("type", "keyword", "ignore_above", 1024)));
                }
            }
            properties.put(property.getName(), field);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (transport != null && !transport.indexSettings().isEmpty()) {
            body.put("settings", transport.indexSettings());
        }
        body.put("mappings", Map.of("properties", properties));
        return SearchJson.write(body);
    }

    /**
     * The dimension of a vector property, declared with {@code @Column(length)}.
     *
     * @param property The property
     * @return The dimension, or {@code -1}
     */
    public static int vectorDimensions(PersistentProperty property) {
        return property.getAnnotationMetadata().intValue("jakarta.persistence.Column", "length").orElse(-1);
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
