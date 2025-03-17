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
package io.micronaut.data.jdbc.sqlserver;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.BookRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.SQL_SERVER)
public abstract class MSBookRepository extends BookRepository {
    public MSBookRepository(MSAuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Query(value = "SELECT book_.* FROM book book_ ORDER BY book_.title ASC OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY")
    public abstract List<Book> findBooks(int limit, int offset);

}
