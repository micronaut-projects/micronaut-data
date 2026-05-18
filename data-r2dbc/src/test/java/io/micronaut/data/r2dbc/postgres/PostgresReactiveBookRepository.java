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
package io.micronaut.data.r2dbc.postgres;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookReactiveRepository;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresReactiveBookRepository extends BookReactiveRepository {

    Book findByTitle(String title);

    Book saveReturning(Book book);

    Iterable<Book> saveReturningAll(Iterable<Book> books);

    @Query("""
        INSERT INTO "book" ("author_id","genre_id","title","total_pages","publisher_id","last_updated")
        VALUES (:authorId, :genreId, :title, :totalPages, :publisherId, :lastUpdated)
         RETURNING *
        """)
    Book customInsertReturningBook(@Nullable Long authorId,
                                   @Nullable Long genreId,
                                   String title,
                                   int totalPages,
                                   @Nullable Long publisherId,
                                   LocalDateTime lastUpdated);

    @Query("""
        INSERT INTO "book" ("author_id","genre_id","title","total_pages","publisher_id","last_updated")
        VALUES (:authorId, :genreId, :title, :totalPages, :publisherId, :lastUpdated)
         RETURNING *
        """)
    Iterable<Book> customInsertReturningBooks(@Nullable Long authorId,
                                              @Nullable Long genreId,
                                              String title,
                                              int totalPages,
                                              @Nullable Long publisherId,
                                              LocalDateTime lastUpdated);

    Book updateReturning(Book book);

    Iterable<Book> updateReturning(Iterable<Book> books);

    String updateReturningTitle(Book book);

    String updateReturningTitle(@Id Long id, String title);

    Iterable<Book> updateReturning(Long authorId);

    Book modifyReturning(Long authorId);

    @Query("""
        UPDATE "book" SET "author_id"=:authorId RETURNING "id","author_id","genre_id","title","total_pages","publisher_id","last_updated"
        """)
    Iterable<Book> customUpdateReturningBooks(Long authorId);

    @Query("""
        UPDATE "book" SET "author_id"=:authorId RETURNING *
        """)
    Book customUpdateReturningBook(Long authorId);

    Book deleteReturning(Book book);

    String deleteReturningTitle(Book book);

    LocalDateTime deleteReturningLastUpdated(Long id, String title);

    LocalDateTime deleteByIdAndTitleReturningLastUpdated(Long id, String title);

    Iterable<Book> deleteReturning(Long authorId);

    Iterable<Book> deleteReturning(Iterable<Book> books);

    @Query("""
        DELETE FROM "book" RETURNING *
        """)
    Iterable<Book> customDeleteAll();

    @Query("""
        DELETE FROM "book" WHERE "id" = :id RETURNING *
        """)
    Book customDeleteOne(Long id);
}
