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
package io.micronaut.data.r2dbc.postgres;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Book;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresReactiveReturningBookRepository {

    Mono<Book> saveReturning(Book book);

    Flux<Book> saveReturningMany(Iterable<Book> books);

    @Query("""
        INSERT INTO "book" ("author_id","genre_id","title","total_pages","publisher_id","last_updated")
        VALUES (:authorId, :genreId, :title, :totalPages, :publisherId, :lastUpdated)
        RETURNING *
        """)
    Mono<Book> insertReturningBook(@Nullable Long authorId,
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
    Flux<Book> insertReturningBooks(@Nullable Long authorId,
                                    @Nullable Long genreId,
                                    String title,
                                    int totalPages,
                                    @Nullable Long publisherId,
                                    LocalDateTime lastUpdated);

    Mono<Book> updateReturning(Book book);

    Mono<Book> deleteReturning(Book book);

    Flux<Book> deleteReturning(Iterable<Book> books);

    @Query("""
        UPDATE "book" SET "author_id"=:authorId WHERE "id" IN (:ids) RETURNING *
        """)
    Flux<Book> customUpdateReturning(Long authorId, Iterable<Long> ids);
}
