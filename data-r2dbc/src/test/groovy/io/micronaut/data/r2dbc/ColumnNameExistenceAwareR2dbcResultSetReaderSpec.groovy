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
package io.micronaut.data.r2dbc

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.DataType
import io.micronaut.data.r2dbc.mapper.ColumnNameExistenceAwareR2dbcResultSetReader
import io.micronaut.data.r2dbc.mapper.ColumnNameR2dbcResultReader
import io.micronaut.data.runtime.convert.DataConversionService
import io.r2dbc.spi.ColumnMetadata
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.ZoneId

class ColumnNameExistenceAwareR2dbcResultSetReaderSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "existence aware reader uses configured delegate conversion service"() {
        given:
        def localDateTime = LocalDateTime.of(2024, 1, 2, 3, 4, 5)
        def instant = localDateTime.atZone(ZoneId.systemDefault()).toInstant()
        def conversionService = context.getBean(DataConversionService)
        def reader = new ColumnNameExistenceAwareR2dbcResultSetReader(new ColumnNameR2dbcResultReader(conversionService))
        Row row = Mock()
        RowMetadata rowMetadata = Mock()
        ColumnMetadata columnMetadata = Mock()

        when:
        def value = reader.readDynamic(row, "last_updated", DataType.TIMESTAMP)

        then:
        value == instant
        reader.readDynamic(row, "missing_column", DataType.TIMESTAMP) == null

        1 * row.getMetadata() >> rowMetadata
        1 * rowMetadata.getColumnMetadatas() >> [columnMetadata]
        1 * columnMetadata.getName() >> "last_updated"
        1 * row.get("last_updated") >> localDateTime
        0 * row.get("missing_column")
    }

    void "column-name reader does not use uppercase fallback when lower-case column is null"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        Row row = Mock()

        when:
        def value = reader.getRequiredValue(row, "col", Object)

        then:
        value == null
        1 * row.get("col", Object) >> null
        1 * row.get("col") >> null
        0 * row.get("COL", _)
        0 * row.get("COL")
    }

    void "column-name reader uses uppercase fallback only when lower-case column is missing"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        Row row = Mock()

        when:
        def value = reader.getRequiredValue(row, "col", String)

        then:
        value == "value"
        1 * row.get("col", String) >> { throw new IllegalArgumentException("No such column: col") }
        1 * row.get("COL", String) >> "value"
        0 * row.get("col")
        0 * row.get("COL")
    }
}
