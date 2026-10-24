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
package io.micronaut.data.jdbc.oraclexe.search;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.SearchRepository;

import java.util.List;
import java.util.UUID;

/**
 * The TCK {@link io.micronaut.data.search.tck.repositories.BookPrefixSearchRepository} methods over {@link OracleJsonBook}.
 */
@JdbcRepository(dialect = Dialect.ORACLE)
public interface OracleJsonBookSearchRepository extends CrudRepository<OracleJsonBook, UUID>, SearchRepository<OracleJsonBook, UUID> {

    List<OracleJsonBook> findByTitleSearch(String query);

    List<OracleJsonBook> findByTitleSearchNative(String query);

    SearchResults<OracleJsonBook> searchByTitleSearchAll(String query);

    List<OracleJsonBook> findBySearchAndAuthor(String query, String author);

    List<OracleJsonBook> findBySearchOrderByPages(String query);

    long countBySearchAll(String query);

    List<OracleJsonBook> findByTitleSearchAndPagesGreaterThan(String query, int pages);

    SearchResults<OracleJsonBook> searchBySearch(String query, Pageable pageable);

    CursoredPage<OracleJsonBook> findAll(CursoredPageable pageable);

    SearchResults<OracleJsonBook> searchByDescriptionSearchPhrase(String query);

    List<OracleJsonBook> findByTitleSearchPrefix(String query);

    List<OracleJsonBook> findByTitleSearchFuzzy(String query);

    SearchResults<OracleJsonBook> searchBySearch(String query, Score minScore);
}
