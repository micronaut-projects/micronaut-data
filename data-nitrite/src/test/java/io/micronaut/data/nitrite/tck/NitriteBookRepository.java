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
package io.micronaut.data.nitrite.tck;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.document.tck.entities.Author;
import io.micronaut.data.document.tck.entities.Book;
import io.micronaut.data.document.tck.repositories.AuthorRepository;
import io.micronaut.data.document.tck.repositories.BookRepository;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder;
import java.util.List;
import java.util.stream.Stream;

@NitriteRepository
@RepositoryConfiguration(queryBuilder = NitriteQueryBuilder.class)
public abstract class NitriteBookRepository extends BookRepository {
    public NitriteBookRepository(AuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Override
    @Query("{}")
    public abstract int deleteByIdAndAuthorId(String id, String authorId);

    @Override
    @Query("{}")
    public abstract List<Book> findByAuthorName(String name);

    @Override
    @Query("{}")
    public abstract Stream<Book> findTop3ByAuthorNameOrderByTitle(String name);

    @Override
    @Query("{}")
    public abstract List<Book> queryTop3ByAuthorNameOrderByTitle(String name);

    @Override
    @Query("{}")
    public abstract List<Book> findByAuthorIsNull();

    @Override
    @Query("{}")
    public abstract List<Book> findByAuthorIsNotNull();

    @Override
    @Query("{}")
    public abstract long updateAuthor(String id, Author author);
}
