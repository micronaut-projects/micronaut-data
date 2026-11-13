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
package io.micronaut.data.search.elasticsearch.memory;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchFieldType;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A searchable entity with keyword, autocomplete and custom analyzer fields, for the index mapping.
 *
 * @param id The id
 * @param cuisine The cuisine, matched exactly
 * @param name The name, completed as typed
 * @param steps The steps, analyzed by whitespace
 * @param reference A reference, mapped as a keyword
 */
@MappedEntity("recipes")
@Searchable(language = "simple")
@Serdeable
public record Recipe(
    @Id @Nullable String id,
    @SearchField(type = SearchFieldType.KEYWORD) String cuisine,
    @SearchField(type = SearchFieldType.AUTOCOMPLETE) String name,
    @SearchField(analyzer = "whitespace") String steps,
    @Nullable UUID reference) {
}
