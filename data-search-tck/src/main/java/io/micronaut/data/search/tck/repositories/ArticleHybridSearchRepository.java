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
package io.micronaut.data.search.tck.repositories;

import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.SearchRepository;
import io.micronaut.data.search.tck.entities.Article;

import java.util.UUID;

/**
 * The repository a backend with text and vector search implements for the hybrid search TCK.
 */
public interface ArticleHybridSearchRepository extends CrudRepository<Article, UUID>, SearchRepository<Article, UUID> {

    SearchResults<Article> searchByTitleSearch(String query);
}
