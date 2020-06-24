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

import io.micronaut.data.annotation.Repository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.repository.CrudRepository
import java.util.stream.Collectors

import javax.transaction.Transactional
import kotlin.streams.toList

@Repository
abstract class AbstractBookRepository(private val jdbcOperations: JdbcOperations) : CrudRepository<Book, Long> {

    @Transactional
    fun findByTitle(title: String): List<Book> {
        val sql = "SELECT * FROM Book AS book WHERE book.title = ?"
        return jdbcOperations.prepareStatement(sql) { statement ->
            statement.setString(1, title)
            val resultSet = statement.executeQuery()
            jdbcOperations.entityStream(resultSet, Book::class.java)
                    .toList()
        }
    }
}
