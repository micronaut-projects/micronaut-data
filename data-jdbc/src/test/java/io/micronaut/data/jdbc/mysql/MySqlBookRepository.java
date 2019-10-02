/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.jdbc.mysql;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.repositories.BookRepository;

import java.util.Arrays;

@JdbcRepository(dialect = Dialect.MYSQL)
public abstract class MySqlBookRepository extends BookRepository {
    public MySqlBookRepository(MySqlAuthorRepository authorRepository) {
        super(authorRepository);
    }

    @Override
    public void setupData() {
        Author king = newAuthor("Stephen King");
        Author jp = newAuthor("James Patterson");
        Author dw = newAuthor("Don Winslow");


        authorRepository.saveAll(Arrays.asList(
                king,
                jp,
                dw
        ));

        saveAll(Arrays.asList(
                newBook(king, "The Stand", 100),
                newBook(king, "Pet Cemetery", 400),
                newBook(jp, "Along Came a Spider", 300),
                newBook(jp, "Double Cross", 300),
                newBook(dw, "The Power of the Dog", 600),
                newBook(dw, "The Border", 700)
        ));
    }
}
