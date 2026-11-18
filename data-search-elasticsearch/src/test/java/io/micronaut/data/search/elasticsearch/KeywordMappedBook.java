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
package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchFieldType;
import io.micronaut.data.annotation.search.SearchMapping;
import io.micronaut.data.annotation.search.Searchable;

/**
 * A searchable entity whose hand-written field mappings declare sub-fields of their own.
 */
@MappedEntity("keyword_mapped_book")
@Searchable
public class KeywordMappedBook {

    @Id
    private String id;
    @SearchField
    @SearchMapping("{\"type\": \"text\", \"fields\": {\"raw\": {\"type\": \"keyword\"}}}")
    private String title;
    @SearchField
    @SearchMapping("{\"type\": \"text\", \"fields\": {\"keyword\": {\"type\": \"keyword\", \"ignore_above\": 100}}}")
    private String subtitle;
    @SearchField(type = SearchFieldType.KEYWORD)
    @SearchMapping("{\"type\": \"keyword\", \"ignore_above\": 64}")
    private String code;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
