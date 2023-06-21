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

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookRepository;

import io.micronaut.core.annotation.Nullable;
import java.util.List;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public abstract class PostgresBookRepository extends BookRepository {
    public PostgresBookRepository(PostgresAuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Query(value = "select * from book where (CASE WHEN :arg0 is not null THEN title = :arg0 ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableSearch(@TypeDef(type = DataType.STRING) @Nullable String arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title IN (:arg0) ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableListSearch(@Nullable List<String> arg0);

    @Query(value = "select * from book where (CASE WHEN exists ( select (:arg0) ) THEN title IN (:arg0) ELSE true END)", nativeQuery = true)
    public abstract List<Book> listNativeBooksNullableArraySearch(@TypeDef(type = DataType.STRING) @Nullable String[] arg0);

    @Where(value = "total_pages > :pages")
    public abstract List<Book> findByTitleStartsWith(String title, int pages);

    @Query(value = "select count(*) from book b where b.title like :title and b.total_pages > :pages", nativeQuery = true)
    public abstract int countNativeByTitleWithPagesGreaterThan(String title, int pages);
}
