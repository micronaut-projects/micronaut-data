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
package io.micronaut.data.jdbc.postgres;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Expandable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.sql.Procedure;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookRepository;

import java.time.LocalDateTime;
import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class PostgresBookRepository extends BookRepository {
    public PostgresBookRepository(PostgresAuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Query(value = "select * from book where (CASE WHEN :arg0 is not null THEN title = :arg0 ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableSearch(@TypeDef(type = DataType.STRING) @Nullable String arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title IN (:arg0) ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableListSearch(@Nullable List<String> arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title IN (:arg0) ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableArraySearch(@Expandable @TypeDef(type = DataType.STRING) @Nullable String[] arg0);

    @Query("SELECT 'one\\:two\\:three'")
    public abstract String reproduceColonErrorEscaped();

    @Procedure
    public abstract int add1(int input);

    @Procedure("add1")
    public abstract int add1Aliased(int input);

    public abstract Book updateReturning(Book book);

    public abstract List<Book> updateReturning(List<Book> books);

    public abstract String updateReturningTitle(Book book);

    public abstract String updateReturningTitle(@Id Long id, String title);

    public abstract String updateByIdReturningTitle(Long id, String title);

    //    @Query(value = "update books set read = true where author = :author returning *", readOnly = false)
    public abstract List<Book> updateReturning(Long authorId);

    public abstract Book modifyReturning(Long authorId);

    @Query("""
        UPDATE "book" SET "author_id"=:authorId RETURNING "id","author_id","genre_id","title","total_pages","publisher_id","last_updated"
        """)
    public abstract List<Book> customUpdateReturningBooks(Long authorId);

    @Query("""
        UPDATE "book" SET "author_id"=:authorId RETURNING *
        """)
    public abstract Book customUpdateReturningBook(Long authorId);

    @Query("""
        INSERT INTO "book" ("author_id","genre_id","title","total_pages","publisher_id","last_updated")
        VALUES (:authorId, :genderId, :title, :totalPages, :publisherId, :lastUpdated)
         RETURNING *
        """)
    public abstract List<Book> customInsertReturningBooks(Long authorId,
                                                          @Nullable Long genderId,
                                                          String title,
                                                          int totalPages,
                                                          @Nullable Long publisherId,
                                                          LocalDateTime lastUpdated);

    @Query("""
        INSERT INTO "book" ("author_id","genre_id","title","total_pages","publisher_id","last_updated")
        VALUES (:authorId, :genderId, :title, :totalPages, :publisherId, :lastUpdated)
         RETURNING *
        """)
    public abstract Book customInsertReturningBook(Long authorId,
                                                   @Nullable Long genderId,
                                                   String title,
                                                   int totalPages,
                                                   @Nullable Long publisherId,
                                                   LocalDateTime lastUpdated);

    public abstract Book saveReturning(Book book);

    public abstract List<Book> saveReturning(List<Book> books);

}
