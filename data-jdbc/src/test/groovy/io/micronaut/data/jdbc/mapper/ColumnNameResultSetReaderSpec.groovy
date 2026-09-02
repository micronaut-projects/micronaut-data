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
package io.micronaut.data.jdbc.mapper

import io.micronaut.data.model.DataType
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

class ColumnNameResultSetReaderSpec extends Specification {

    @Shared
    @AutoCleanup
    Connection connection = DriverManager.getConnection("jdbc:h2:mem:columnnamereader;DB_CLOSE_DELAY=-1")

    @Shared
    ColumnNameResultSetReader reader = new ColumnNameResultSetReader()

    void "resolves column ordinals of the result set"() {
        given:
        Statement statement = connection.createStatement()
        ResultSet resultSet = statement.executeQuery("SELECT 'A' AS first_name, 42 AS age, TIMESTAMP '2024-01-02 03:04:05' AS last_updated")

        expect:
        reader.findColumnIndex(resultSet, "first_name") == 1
        reader.findColumnIndex(resultSet, "age") == 2
        reader.findColumnIndex(resultSet, "LAST_UPDATED") == 3
        reader.findColumnIndex(resultSet, "missing") == -1

        cleanup:
        resultSet.close()
        statement.close()
    }

    void "column index reader reads the same values as the column name reader"() {
        given:
        Statement statement = connection.createStatement()
        ResultSet resultSet = statement.executeQuery("SELECT 'A' AS first_name, 42 AS age, NULL AS nickname, TIMESTAMP '2024-01-02 03:04:05' AS last_updated")
        def indexReader = reader.getColumnIndexReader()

        expect:
        indexReader != null
        resultSet.next()
        indexReader.readDynamic(resultSet, reader.findColumnIndex(resultSet, "first_name"), DataType.STRING) == reader.readDynamic(resultSet, "first_name", DataType.STRING)
        indexReader.readDynamic(resultSet, reader.findColumnIndex(resultSet, "age"), DataType.INTEGER) == reader.readDynamic(resultSet, "age", DataType.INTEGER)
        indexReader.readDynamic(resultSet, reader.findColumnIndex(resultSet, "nickname"), DataType.STRING) == null
        indexReader.readDynamic(resultSet, reader.findColumnIndex(resultSet, "last_updated"), DataType.TIMESTAMP) == reader.readDynamic(resultSet, "last_updated", DataType.TIMESTAMP)

        cleanup:
        resultSet.close()
        statement.close()
    }

    void "existence aware reader delegates column ordinal resolution"() {
        given:
        Statement statement = connection.createStatement()
        ResultSet resultSet = statement.executeQuery("SELECT 'A' AS first_name")
        def existenceAwareReader = new ColumnNameExistenceAwareResultSetReader(reader)

        expect:
        existenceAwareReader.findColumnIndex(resultSet, "first_name") == 1
        existenceAwareReader.findColumnIndex(resultSet, "missing") == -1
        existenceAwareReader.getColumnIndexReader().is(reader.getColumnIndexReader())

        cleanup:
        resultSet.close()
        statement.close()
    }
}
