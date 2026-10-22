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

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.SearchRepository;
import io.micronaut.data.search.tck.entities.Book;

import java.util.List;

/**
 * The portable repository every full-text search backend implements for the shared TCK.
 * Phrase and prefix matching are declared on {@link BookPhraseSearchRepository} and
 * {@link BookPrefixSearchRepository} so a backend without them still compiles.
 */
public interface BookSearchRepository extends CrudRepository<Book, Long>, SearchRepository<Book, Long> {

    List<Book> findByTitleSearch(String query);

    SearchResults<Book> searchByTitleSearchAll(String query);

    List<Book> findBySearchAndAuthor(String query, String author);

    List<Book> findBySearchOrderByPages(String query);

    long countBySearchAll(String query);

    List<Book> findByTitleSearchAndPagesGreaterThan(String query, int pages);

    SearchResults<Book> searchBySearch(String query, Pageable pageable);
}
