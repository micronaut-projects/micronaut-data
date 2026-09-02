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
package io.micronaut.data.r2dbc.mapper

import io.micronaut.data.model.DataType
import io.r2dbc.spi.ColumnMetadata
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import io.r2dbc.spi.Type
import spock.lang.Specification

class ColumnNameR2dbcResultReaderSpec extends Specification {

    static final List<String> COLUMNS = ["id", "title", "pages"]

    void "resolves the ordinal of a column from the row metadata"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def row = new StubRow([1L, "Book", 100])

        expect:
        reader.findColumnIndex(row, "id") == 0
        reader.findColumnIndex(row, "title") == 1
        reader.findColumnIndex(row, "pages") == 2
    }

    void "matches the column name ignoring case, like the drivers do"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def row = new StubRow([1L, "Book", 100])

        expect:
        reader.findColumnIndex(row, "TITLE") == 1
    }

    void "reports an unknown column so the caller keeps reading by name"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def row = new StubRow([1L, "Book", 100])

        expect:
        reader.findColumnIndex(row, "missing") == -1
    }

    void "ties the resolved ordinals to the row metadata, which is shared by the rows of one result"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def metadata = new StubRowMetadata()
        def first = new StubRow([1L, "Book 1", 100], metadata)
        def second = new StubRow([2L, "Book 2", 200], metadata)

        expect: "the rows differ but share the key, so the ordinals survive from one row to the next"
        !first.is(second)
        reader.columnResolutionKey(first).is(reader.columnResolutionKey(second))
    }

    void "reading by the resolved ordinal returns what reading by name returns"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def indexReader = reader.getColumnIndexReader()
        def row = new StubRow([1L, "Book", 100])

        expect:
        indexReader != null
        indexReader.readDynamic(row, reader.findColumnIndex(row, "id"), DataType.LONG) ==
                reader.readDynamic(row, "id", DataType.LONG)
        indexReader.readDynamic(row, reader.findColumnIndex(row, "title"), DataType.STRING) ==
                reader.readDynamic(row, "title", DataType.STRING)
        indexReader.readDynamic(row, reader.findColumnIndex(row, "pages"), DataType.INTEGER) ==
                reader.readDynamic(row, "pages", DataType.INTEGER)
    }

    static class StubColumnMetadata implements ColumnMetadata {
        final String name

        StubColumnMetadata(String name) {
            this.name = name
        }

        @Override
        String getName() {
            return name
        }

        @Override
        Type getType() {
            throw new UnsupportedOperationException()
        }
    }

    static class StubRowMetadata implements RowMetadata {
        @Override
        ColumnMetadata getColumnMetadata(int index) {
            return new StubColumnMetadata(COLUMNS[index])
        }

        @Override
        ColumnMetadata getColumnMetadata(String name) {
            return new StubColumnMetadata(name)
        }

        @Override
        List<? extends ColumnMetadata> getColumnMetadatas() {
            return COLUMNS.collect { new StubColumnMetadata(it) }
        }
    }

    static class StubRow implements Row {
        final List<Object> values
        final RowMetadata metadata

        StubRow(List<Object> values, RowMetadata metadata = new StubRowMetadata()) {
            this.values = values
            this.metadata = metadata
        }

        @Override
        RowMetadata getMetadata() {
            return metadata
        }

        @Override
        <T> T get(int index, Class<T> type) {
            return convert(values[index], type)
        }

        @Override
        <T> T get(String name, Class<T> type) {
            return convert(values[indexOf(name)], type)
        }

        @Override
        Object get(int index) {
            return values[index]
        }

        @Override
        Object get(String name) {
            return values[indexOf(name)]
        }

        private static <T> T convert(Object value, Class<T> type) {
            if (value == null) {
                return null
            }
            return (T) (type.isInstance(value) ? value : value.asType(type))
        }

        private static int indexOf(String name) {
            int index = COLUMNS.indexOf(name.toLowerCase())
            if (index == -1) {
                throw new NoSuchElementException(name)
            }
            return index
        }
    }
}
