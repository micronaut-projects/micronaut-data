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
package io.micronaut.data.jdbc.h2;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
public abstract class H2BookRepository extends io.micronaut.data.tck.repositories.BookRepository {

    public H2BookRepository(H2AuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Query("UPDATE book SET total_pages = :pages WHERE title = :title")
    abstract Long setPages(int pages, String title);

    @Query("DELETE book WHERE title = :title")
    abstract Long wipeOutBook(String title);

    @Where(value = "total_pages > :pages")
    abstract List<Book> findByTitleStartsWith(String title, int pages);

    @Query(value = "select count(*) from book b where b.title like :title and b.total_pages > :pages", nativeQuery = true)
    abstract int countNativeByTitleWithPagesGreaterThan(String title, int pages);

    @Query("SELECT b.*, p_.id AS p_id, p_.num AS p_num, c_.id AS c_id, c_.title AS c_title, c_.pages AS c_pages" +
        " FROM book b LEFT JOIN page p_ ON p_.book_id = b.id AND p_.num > :pageNum LEFT JOIN chapter c_ ON c_.book_id = b.id WHERE b.title = :title")
    @Join(value = "pages", alias = "p_")
    @Join(value = "chapters", alias = "c_")
    abstract List<Book> findCustomByTitleAndPageGreaterThanWithPagesAndChapters(String title, int pageNum);
}
