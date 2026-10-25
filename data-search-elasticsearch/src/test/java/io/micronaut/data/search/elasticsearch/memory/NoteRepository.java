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

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.SearchRepository;
import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/**
 * A repository over the in-memory transport.
 */
@ElasticsearchRepository
public interface NoteRepository extends PageableRepository<Note, String>, SearchRepository<Note, String> {

    List<Note> findByTitle(String title);

    @Nullable
    Note getByTitle(String title);

    long countByTitle(String title);

    boolean existsByTitle(String title);

    long deleteByTitle(String title);

    Page<Note> findByPagesGreaterThan(int pages, Pageable pageable);

    List<Note> findByPagesLessThan(int pages, Pageable pageable);

    Stream<Note> findByBody(String body);

    SearchResults<Note> searchByTitleSearch(String query);

    List<NoteSummary> findByBodySearch(String query);

    List<Note> findByTitleStartsWith(String prefix);

    List<Note> findByTitleStartsWithIgnoreCase(String prefix);

    List<Note> findByTitleEndsWith(String suffix);

    List<Note> findByTitleContains(String part);

    List<Note> findByTitleLike(String pattern);

    List<Note> findByTitleIgnoreCase(String title);

    List<Note> findByTitleRegex(String pattern);

    List<Note> findByPagesGreaterThanEqualsAndPagesLessThanEquals(int min, int max);

    List<Note> findByTitleIsEmpty();

    List<Note> findByTitleIsNotEmpty();

    List<Note> findByTitleOrBody(String title, String body);

    List<Note> findByTitleNotInList(List<String> titles);

    List<Note> findByTitleNotEqual(String title);
}
