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
package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.AuthorRepository;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Repository
@Transactional
public abstract class BookRepository extends io.micronaut.data.tck.repositories.BookRepository {
    public BookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Query(value = "select * from book b where b.title like :t limit 5", nativeQuery = true)
    abstract List<Book> listNativeBooks(String t);

    @EntityGraph(
            attributePaths = {
                    "totalPages",
                    "author"
            }
    )
    abstract List<Book> findAllByTitleStartsWith(String text);

    @Where(value = "total_pages > :pages")
    abstract List<Book> findByTitleStartsWith(String title, int pages);

    @Query(value = "select count(*) from book b where b.title like :title and b.total_pages > :pages", nativeQuery = true)
    abstract int countNativeByTitleWithPagesGreaterThan(String title, int pages);
}
