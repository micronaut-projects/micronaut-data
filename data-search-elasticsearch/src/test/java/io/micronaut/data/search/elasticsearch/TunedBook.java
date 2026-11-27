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

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.SearchIndexSettings;
import io.micronaut.data.annotation.search.SearchMapping;
import io.micronaut.data.annotation.search.Searchable;

/**
 * A searchable entity with its own index settings and a hand-written field mapping.
 */
@MappedEntity("tuned_book")
@Searchable
@SearchIndexSettings("{\"number_of_shards\": 2, \"analysis\": {\"analyzer\": {\"folded\": {\"tokenizer\": \"standard\", \"filter\": [\"lowercase\", \"asciifolding\"]}}}}")
public class TunedBook {

    @Id
    @GeneratedValue
    private String id;
    @SearchField
    @SearchMapping("{\"type\": \"text\", \"analyzer\": \"folded\", \"fielddata\": true}")
    private String title;
    @SearchMapping("{\"type\": \"keyword\", \"ignore_above\": 64}")
    private String isbn;

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

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }
}
