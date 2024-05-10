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
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
public abstract class H2HeadlessBookRepository {
    @Query(value = "select * from book where title like :title", nativeQuery = true)
    abstract List<Book> findByPattern(String title);

    // Could not resolve root entity. Either implement the Repository interface or define the entity as part of the signature
    // @Query(value = "select count(*) from book", nativeQuery = true)
    // abstract Integer countBooks();
}
