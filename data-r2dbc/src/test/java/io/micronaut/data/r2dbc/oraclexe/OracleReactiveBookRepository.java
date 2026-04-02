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
package io.micronaut.data.r2dbc.oraclexe;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookReactiveRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@R2dbcRepository(dialect = Dialect.ORACLE)
public interface OracleReactiveBookRepository extends BookReactiveRepository {

    @Query("""
        INSERT INTO "BOOK" ("AUTHOR_ID","GENRE_ID","TITLE","TOTAL_PAGES","PUBLISHER_ID","LAST_UPDATED")
        VALUES (:authorId, :genreId, :title, :totalPages, :publisherId, :lastUpdated)
        RETURNING *
        """)
    Mono<Book> customInsertReturningBook(Long authorId, Long genreId, String title, int totalPages, Long publisherId, java.time.LocalDateTime lastUpdated);

    @Query("""
        INSERT INTO "BOOK" ("AUTHOR_ID","GENRE_ID","TITLE","TOTAL_PAGES","PUBLISHER_ID","LAST_UPDATED")
        VALUES (:authorId, :genreId, :title, :totalPages, :publisherId, :lastUpdated)
        RETURNING *
        """)
    Flux<Book> customInsertReturningBooks(Long authorId, Long genreId, String title, int totalPages, Long publisherId, java.time.LocalDateTime lastUpdated);

    @Query("""
        INSERT INTO "BOOK" ("AUTHOR_ID","GENRE_ID","TITLE","TOTAL_PAGES","PUBLISHER_ID","LAST_UPDATED")
        VALUES (:authorId, :genreId, :title, :totalPages, :publisherId, :lastUpdated)
        RETURNING "TITLE"
        """)
    Mono<String> customInsertReturningTitle(Long authorId, Long genreId, String title, int totalPages, Long publisherId, java.time.LocalDateTime lastUpdated);

    @Query("UPDATE \"BOOK\" SET \"TITLE\"=:title,\"TOTAL_PAGES\"=:totalPages,\"LAST_UPDATED\"=:lastUpdated WHERE \"ID\" = :bookId RETURNING *")
    Mono<Book> customUpdateReturning(Long bookId, String title, int totalPages, java.time.LocalDateTime lastUpdated);

    @Query("DELETE FROM \"BOOK\" WHERE \"ID\" = :bookId RETURNING \"TITLE\"")
    Mono<String> customDeleteReturningTitle(Long bookId);

    Flux<Book> deleteReturning(Iterable<Book> books);

    Mono<Book> saveReturning(Book book);

    Flux<Book> saveReturningAll(Iterable<Book> books);

    Mono<Book> updateReturning(Book book);

    Mono<Book> deleteReturning(Book book);
}
