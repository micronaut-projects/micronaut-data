/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.hibernate.reactive;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.reactive.entities.AuthorDto;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.tck.entities.Author;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.constraints.NotNull;

@Repository
public interface AuthorRepository extends ReactorCrudRepository<Author, Long> {

    @NonNull
    @EntityGraph(attributePaths = {"books", "books.pages"})
    Mono<Author> queryById(@NonNull @NotNull Long aLong);

    @Query("select new io.micronaut.data.hibernate.reactive.entities.AuthorDto(e.id, e.name) from Author e")
    Flux<AuthorDto> getAuthors();

    @Query("select new io.micronaut.data.hibernate.reactive.entities.AuthorDto(e.id, e.name) from Author e where e.id = :id")
    Mono<AuthorDto> getAuthorsById(Long id);

    @Query(nativeQuery = true, value = "SELECT id FROM (VALUES (1),(2),(4),(5)) AS t(id)")
    Flux<Long> longs();

    @Query(value = "select id as authorId, name as authorName from Author", nativeQuery = true)
    Flux<AuthorDto> getAuthorsByNativeQuery();

    Mono<Author> findByBooksTitle(String title);

    Mono<Author> findByName(String name);

    @Join("books")
    Mono<Author> searchByName(String name);

    @Override
    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    @Join(value = "books.pages", type = Join.Type.LEFT_FETCH)
    Mono<Author> findById(Long aLong);
}
