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
package io.micronaut.data.tck.repositories;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.repository.jpa.criteria.QuerySpecification;
import io.micronaut.data.tck.entities.Author;

import io.micronaut.core.annotation.Nullable;

import io.micronaut.data.tck.entities.AuthorDtoWithBooks;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface AuthorRepository extends CrudRepository<Author, Long>, JpaSpecificationExecutor<Author> {

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    Author queryByName(String name);

    @NonNull
    @Override
    @Join(value = "books", alias = "b", type = Join.Type.LEFT_FETCH)
    @Join(value = "books.pages", alias = "bp", type = Join.Type.LEFT_FETCH)
    Optional<Author> findById(@NonNull @NotNull Long aLong);

    @Override
    @Join(value = "books.pages", alias = "bp", type = Join.Type.LEFT_FETCH)
    @Join(value = "books", alias = "b", type = Join.Type.LEFT_FETCH)
    Optional<Author> findOne(PredicateSpecification<Author> specification);

    @Override
    @Join(value = "books.pages", alias = "bp", type = Join.Type.LEFT_FETCH)
    List<Author> findAll(PredicateSpecification<Author> specification);

    @Override
    @Join(value = "books.pages", type = Join.Type.LEFT_FETCH)
    Optional<Author> findOne(QuerySpecification<Author> specification);

    Author findByName(String name);

    Author findByBooksTitle(String title);

    long countByNameContains(String text);

    Author findByNameStartsWith(String name);

    Author findByNameStartsWithIgnoreCase(String name);

    List<Author> findByNameContains(String name);

    List<Author> findByNameContainsIgnoreCase(String name);

    Stream<Author> queryByNameContains(String name);

    Author findByNameEndsWithIgnoreCase(String name);

    Author findByNameEndsWith(String name);

    Author findByNameIgnoreCase(String name);

    @Join("books")
    Author searchByName(String name);

    @Nullable
    @Join("books")
    Author retrieveByName(String name);

    // Various list all authors with different join types:

    @Join("books")
    List<Author> listAll();

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    List<Author> findByIdIsNotNull();

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    Stream<Author> queryByIdIsNotNull();

    @Join(value = "books", type = Join.Type.RIGHT_FETCH)
    List<Author> findByNameIsNotNull();

    List<AuthorDtoWithBooks> searchAll();

    @Join("books")
    List<AuthorDtoWithBooks> queryAll();

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    List<AuthorDtoWithBooks> retrieveByIdIsNotNull();

    @Join(value = "books", type = Join.Type.RIGHT_FETCH)
    List<AuthorDtoWithBooks> searchByNameIsNotNull();

    List<AuthorDtoWithBooks> readAll();

    @Join("books")
    List<AuthorDtoWithBooks> read();

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    List<AuthorDtoWithBooks> readByIdIsNotNull();

    @Join(value = "books", type = Join.Type.RIGHT_FETCH)
    List<AuthorDtoWithBooks> readByNameIsNotNull();

    void updateNickname(@Id Long id, @Parameter("nickName") @Nullable String nickName);

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    Page<Author> findAll(Pageable pageable);

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    @Where("@.nick_name = :nickName")
    Page<Author> findAllByName(String name, String nickName, Pageable pageable);

    @Where("@.name = :name")
    Page<Author> findAllByNickName(String nickName, String name, Pageable pageable);

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    CursoredPage<Author> findByBooksTotalPages(int totalPages, CursoredPageable pageable);

    @Join(value = "books", alias = "b_")
    @Query("""
        SELECT author_.*,
               b_.id AS b_id, b_.author_id AS b_author_id, b_.genre_id AS b_genre_id,
               b_.title AS b_title, b_.total_pages AS b_total_pages, b_.publisher_id AS b_publisher_id,
               b_.last_updated AS b_last_updated
        FROM author author_ INNER JOIN book b_ ON author_.id = b_.author_id
        WHERE author_.name = :name
        """)
    List<Author> findAllByNameCustom(String name);

    final class Specifications {

        private Specifications() {
        }

        static PredicateSpecification<Author> authorNameEquals(String name) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get("name"), name);
        }

        static QuerySpecification<Author> authorIdEquals(Long id) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("id"), id);
        }
    }
}
