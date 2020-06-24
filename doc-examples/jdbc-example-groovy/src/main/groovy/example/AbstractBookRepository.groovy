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
package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

import javax.transaction.Transactional
import java.sql.ResultSet
import java.util.List
import java.util.stream.Collectors

@JdbcRepository(dialect = Dialect.H2)
abstract class AbstractBookRepository implements CrudRepository<Book, Long> {

    private final JdbcOperations jdbcOperations

    AbstractBookRepository(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations
    }

    @Transactional
    List<Book> findByTitle(String title) {
        String sql = "SELECT * FROM Book AS book WHERE book.title = ?"
        return jdbcOperations.prepareStatement(sql,  { statement ->
            statement.setString(1, title)
            ResultSet resultSet = statement.executeQuery()
            return jdbcOperations.entityStream(resultSet, Book.class)
                    .collect(Collectors.toList())
        })
    }
}
