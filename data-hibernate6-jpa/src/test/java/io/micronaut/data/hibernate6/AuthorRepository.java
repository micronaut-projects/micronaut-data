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
package io.micronaut.data.hibernate6;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate6.entities.AuthorDto;
import io.micronaut.data.hibernate6.jpa.annotation.EntityGraph;
import io.micronaut.data.tck.entities.Author;

import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface AuthorRepository extends io.micronaut.data.tck.repositories.AuthorRepository {

    @NonNull
    @EntityGraph(attributePaths = {"books", "books.pages"})
    Optional<Author> queryById(@NonNull @NotNull Long aLong);

    @Query("select new io.micronaut.data.hibernate6.entities.AuthorDto(e.id, e.name) from Author e")
    List<AuthorDto> getAuthors();

    @Query("select new io.micronaut.data.hibernate6.entities.AuthorDto(e.id, e.name) from Author e where e.id = :id")
    AuthorDto getAuthorsById(Long id);

    @Query(nativeQuery = true, value = "SELECT id FROM (VALUES (1),(2),(4),(5)) AS t(id)")
    List<Long> longs();

    @Query(value = "select id as authorId, name as authorName from Author", nativeQuery = true)
    List<AuthorDto> getAuthorsByNativeQuery();

}
