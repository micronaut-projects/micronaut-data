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

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.mapper.SqlResultConsumer;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.AuthorDTO;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialectName = "H2")
public interface H2AuthorRepository extends io.micronaut.data.tck.repositories.AuthorRepository  {
    @Query("select *, author.name as author_name, author.nick_name as author_nick_name from book as book inner join author as author where book.title = :title and book.total_pages > :pages")
    Book customSearch(String title, int pages, SqlResultConsumer<Book> mappingFunction);

    default Book testReadSingleProperty(String title, int pages) {
        return customSearch(title, pages, (book, context) -> {
            Author author = new Author();
            author.setName(context.readString("author_name"));
            book.setAuthor(author);
        });
    }

    default Book testReadAssociatedEntity(String title, int pages) {
        return customSearch(title, pages, (book, context) -> book.setAuthor(context.readEntity("author_", Author.class)));
    }

    default Book testReadDTO(String title, int pages) {
        return customSearch(title, pages, (book, context) ->
                {
                    AuthorDTO dto = context.readDTO("author_", Author.class, AuthorDTO.class);
                    Author author = new Author();
                    author.setName(dto.getName());
                    book.setAuthor(author);
                }
        );
    }
}
