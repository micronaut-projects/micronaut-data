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
package io.micronaut.data.jdbc

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.mapper.ColumnNameExistenceAwareResultSetReader
import io.micronaut.data.jdbc.mapper.ColumnNameResultSetReader
import io.micronaut.data.model.DataType
import io.micronaut.data.runtime.convert.DataConversionService
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.DriverManager
import java.time.LocalDateTime

class ColumnNameExistenceAwareResultSetReaderSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "existence aware reader uses configured delegate conversion service"() {
        given:
        def conversionService = context.getBean(DataConversionService)
        def reader = new ColumnNameExistenceAwareResultSetReader(new ColumnNameResultSetReader(conversionService))
        def connection = DriverManager.getConnection("jdbc:h2:mem:readerconversion;DB_CLOSE_DELAY=-1")
        def statement = connection.createStatement()
        def resultSet = statement.executeQuery("SELECT TIMESTAMP '2024-01-02 03:04:05' AS last_updated")

        expect:
        resultSet.next()
        def timestamp = reader.readDynamic(resultSet, "last_updated", DataType.TIMESTAMP)
        reader.convertRequired(timestamp, LocalDateTime) == LocalDateTime.of(2024, 1, 2, 3, 4, 5)

        cleanup:
        resultSet?.close()
        statement?.close()
        connection?.close()
    }
}
