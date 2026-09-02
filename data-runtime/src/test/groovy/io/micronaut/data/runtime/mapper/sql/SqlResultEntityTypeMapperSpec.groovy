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
package io.micronaut.data.runtime.mapper.sql

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.mapper.ResultReader
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SqlResultEntityTypeMapperSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    RuntimePersistentEntity<MappedBook> entity = new RuntimePersistentEntity<>(MappedBook)

    void "column ordinals are resolved once per result set and reused for every row"() {
        given:
        def reader = new IndexAwareReader()
        def resultSet = new FakeResultSet(["id", "title", "pages"], [
                [1L, "Book 1", 100],
                [2L, "Book 2", 200],
                [3L, "Book 3", 300]
        ])

        when:
        List<MappedBook> books = readAll(reader, resultSet)

        then:
        books*.id == [1L, 2L, 3L]
        books*.title == ["Book 1", "Book 2", "Book 3"]
        books*.pages == [100, 200, 300]
        reader.resolvedColumns == ["id", "title", "pages"]
        reader.indexReads == 9
        reader.nameReads == 0
    }

    void "reads by name the columns whose ordinal cannot be resolved"() {
        given:
        def reader = new IndexAwareReader(unresolvableColumns: ["pages"])
        def resultSet = new FakeResultSet(["id", "title", "pages"], [
                [1L, "Book 1", 100],
                [2L, "Book 2", 200]
        ])

        when:
        List<MappedBook> books = readAll(reader, resultSet)

        then:
        books*.pages == [100, 200]
        reader.resolvedColumns == ["id", "title", "pages"]
        reader.indexReads == 4
        reader.nameReads == 2
    }

    void "reads by name when the reader doesn't support column ordinals"() {
        given:
        def reader = new NameOnlyReader()
        def resultSet = new FakeResultSet(["id", "title", "pages"], [
                [1L, "Book 1", 100],
                [2L, "Book 2", 200]
        ])

        when:
        List<MappedBook> books = readAll(reader, resultSet)

        then:
        books*.title == ["Book 1", "Book 2"]
        reader.nameReads == 6
    }

    void "column ordinals are resolved again for a different result set"() {
        given:
        def reader = new IndexAwareReader()
        def mapper = createMapper(reader)
        def resultSet1 = new FakeResultSet(["id", "title", "pages"], [[1L, "Book 1", 100]])
        def resultSet2 = new FakeResultSet(["pages", "title", "id"], [[200, "Book 2", 2L]])

        when:
        MappedBook book1 = mapper.readEntity(resultSet1.next())
        MappedBook book2 = mapper.readEntity(resultSet2.next())

        then:
        book1.id == 1L
        book1.title == "Book 1"
        book1.pages == 100
        book2.id == 2L
        book2.title == "Book 2"
        book2.pages == 200
        reader.resolvedColumns == ["id", "title", "pages", "id", "title", "pages"]
        reader.nameReads == 0
    }

    private List<MappedBook> readAll(ResultReader<FakeResultSet, String> reader, FakeResultSet resultSet) {
        def pushingMapper = createMapper(reader).readManyMapper()
        while (resultSet.next()) {
            pushingMapper.processRow(resultSet)
        }
        return pushingMapper.result
    }

    private SqlResultEntityTypeMapper<FakeResultSet, MappedBook> createMapper(ResultReader<FakeResultSet, String> reader) {
        return new SqlResultEntityTypeMapper<FakeResultSet, MappedBook>(
                entity,
                reader,
                Set.of(),
                null,
                context.getBean(DataConversionService)
        )
    }

    /**
     * A result set holding all rows, the same instance is used for every row like a JDBC result set.
     */
    static class FakeResultSet {
        final List<String> columns
        final List<List<Object>> rows
        int cursor = -1

        FakeResultSet(List<String> columns, List<List<Object>> rows) {
            this.columns = columns
            this.rows = rows
        }

        FakeResultSet next() {
            cursor++
            return cursor < rows.size() ? this : null
        }

        Object get(int index) {
            return rows[cursor][index]
        }

        Object get(String column) {
            int index = columns.indexOf(column)
            if (index == -1) {
                throw new DataAccessException("Unknown column: " + column)
            }
            return get(index)
        }
    }

    static class NameOnlyReader implements ResultReader<FakeResultSet, String> {
        int nameReads = 0

        @Override
        <T> T getRequiredValue(FakeResultSet resultSet, String name, Class<T> type) throws DataAccessException {
            nameReads++
            return (T) resultSet.get(name)
        }

        @Override
        boolean next(FakeResultSet resultSet) {
            return resultSet.next() != null
        }
    }

    static class IndexAwareReader extends NameOnlyReader {
        List<String> resolvedColumns = []
        Set<String> unresolvableColumns = []
        int indexReads = 0
        private final ResultReader<FakeResultSet, Integer> indexReader = new ResultReader<FakeResultSet, Integer>() {
            @Override
            <T> T getRequiredValue(FakeResultSet resultSet, Integer index, Class<T> type) throws DataAccessException {
                indexReads++
                return (T) resultSet.get(index)
            }

            @Override
            boolean next(FakeResultSet resultSet) {
                return resultSet.next() != null
            }
        }

        @Override
        int findColumnIndex(FakeResultSet resultSet, String columnName) {
            resolvedColumns.add(columnName)
            if (unresolvableColumns.contains(columnName)) {
                return -1
            }
            return resultSet.columns.indexOf(columnName)
        }

        @Override
        ResultReader<FakeResultSet, Integer> getColumnIndexReader() {
            return indexReader
        }
    }
}
