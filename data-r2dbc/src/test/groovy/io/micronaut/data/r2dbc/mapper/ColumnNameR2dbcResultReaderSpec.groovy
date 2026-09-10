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

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.DataType
import io.micronaut.data.runtime.convert.DataConversionService
import io.r2dbc.spi.Clob
import io.r2dbc.spi.ColumnMetadata
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import io.r2dbc.spi.Type
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.ByteBuffer
import java.sql.Time
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ColumnNameR2dbcResultReaderSpec extends Specification {

    static final List<String> MODES = ["name", "ordinal"]

    static final List<List<Object>> TYPED_READS = [
            ["readLong", 5L, 0L],
            ["readInt", 5, 0],
            ["readShort", (short) 5, (short) 0],
            ["readByte", (byte) 5, (byte) 0],
            ["readDouble", 5.5d, 0d],
            ["readFloat", 5.5f, 0f],
            ["readBoolean", true, false],
            ["readChar", 'x' as char, (char) 0],
            ["readBigDecimal", 5.5G, null],
    ]

    static final byte[] BYTES = [1, 2, 3] as byte[]

    static final List<List<Object>> DYNAMIC_READS = [
            [DataType.UUID, UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000001")],
            [DataType.BOOLEAN, true, true],
            [DataType.BYTE, (byte) 3, (byte) 3],
            [DataType.DOUBLE, 2.5d, 2.5d],
            [DataType.BIGDECIMAL, 2.5G, 2.5G],
            [DataType.TIMESTAMP, Instant.parse("2024-01-02T03:04:05Z"), Instant.parse("2024-01-02T03:04:05Z")],
            [DataType.DATE, LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2)],
            [DataType.TIME, Time.valueOf("10:11:12"), Time.valueOf("10:11:12")],
            [DataType.CHARACTER, 'c' as char, 'c' as char],
            [DataType.FLOAT, "2.5", 2.5f],
            [DataType.SHORT, null, null],
            [DataType.BYTE_ARRAY, BYTES, BYTES],
            [DataType.OBJECT, "anything", "anything"],
    ]

    void "resolves the ordinal of a column from the row metadata"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def row = new StubRow([id: 1L, title: "Book", pages: 100])

        expect:
        reader.findColumnIndex(row, "id") == 0
        reader.findColumnIndex(row, "title") == 1
        reader.findColumnIndex(row, "pages") == 2
    }

    void "matches the column name ignoring case, like the drivers do"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def row = new StubRow([id: 1L, title: "Book", pages: 100])

        expect:
        reader.findColumnIndex(row, "TITLE") == 1
    }

    void "reports an unknown column so the caller keeps reading by name"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def row = new StubRow([id: 1L, title: "Book", pages: 100])

        expect:
        reader.findColumnIndex(row, "missing") == -1
    }

    void "keeps reading by name when the row metadata is unavailable"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def row = new StubRow([id: 1L], null, false)

        expect: "no ordinal, and the row itself as the key so nothing is shared between rows"
        reader.findColumnIndex(row, "id") == -1
        reader.columnResolutionKey(row).is(row)
    }

    void "ties the resolved ordinals to the row metadata, which is shared by the rows of one result"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def metadata = new StubRowMetadata(["id", "title", "pages"])
        def first = new StubRow([id: 1L, title: "Book 1", pages: 100], metadata)
        def second = new StubRow([id: 2L, title: "Book 2", pages: 200], metadata)

        expect: "the rows differ but share the key, so the ordinals survive from one row to the next"
        !first.is(second)
        reader.columnResolutionKey(first).is(reader.columnResolutionKey(second))
    }

    void "reading by the resolved ordinal returns what reading by name returns"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def indexReader = reader.getColumnIndexReader()
        def row = new StubRow([id: 1L, title: "Book", pages: 100])

        expect:
        indexReader != null
        indexReader.readDynamic(row, reader.findColumnIndex(row, "id"), DataType.LONG) ==
                reader.readDynamic(row, "id", DataType.LONG)
        indexReader.readDynamic(row, reader.findColumnIndex(row, "title"), DataType.STRING) ==
                reader.readDynamic(row, "title", DataType.STRING)
        indexReader.readDynamic(row, reader.findColumnIndex(row, "pages"), DataType.INTEGER) ==
                reader.readDynamic(row, "pages", DataType.INTEGER)
    }

    @Unroll
    void "#readMethod reads the value, and the default for a null, by #mode"() {
        given:
        def row = new StubRow([present: value, absent: null])

        expect:
        read(mode, row, "present") { r, c -> r."$readMethod"(row, c) } == value
        read(mode, row, "absent") { r, c -> r."$readMethod"(row, c) } == defaultValue

        where:
        [mode, entry] << [MODES, TYPED_READS].combinations()
        readMethod = entry[0]
        value = entry[1]
        defaultValue = entry[2]
    }

    @Unroll
    void "reads dates and timestamps as java.util.Date, by #mode"() {
        given:
        def date = LocalDate.of(2024, 1, 2)
        def timestamp = LocalDateTime.of(2024, 1, 2, 3, 4, 5)
        def row = new StubRow([date: date, timestamp: timestamp, none: null])

        expect:
        read(mode, row, "date") { r, c -> r.readDate(row, c) } == java.sql.Date.valueOf(date)
        read(mode, row, "timestamp") { r, c -> r.readTimestamp(row, c) } ==
                Date.from(timestamp.atZone(ZoneId.systemDefault()).toInstant())
        read(mode, row, "none") { r, c -> r.readDate(row, c) } == null
        read(mode, row, "none") { r, c -> r.readTimestamp(row, c) } == null

        where:
        mode << MODES
    }

    @Unroll
    void "reads a string from a string, a clob, or a value the driver will not hand out as a string, by #mode"() {
        given:
        def row = new StubRow([
                text  : "plain",
                clob  : new StubClob("from clob"),
                empty : new StubClob(null),
                number: 42,
                internal: new Typed(new StringBuilder("pg"), [(String): "rendered"]),
                none  : null,
        ])

        expect:
        read(mode, row, "text") { r, c -> r.readString(row, c) } == "plain"
        read(mode, row, "clob") { r, c -> r.readString(row, c) } == "from clob"
        read(mode, row, "empty") { r, c -> r.readString(row, c) } == null
        read(mode, row, "number") { r, c -> r.readString(row, c) } == "42"
        read(mode, row, "internal") { r, c -> r.readString(row, c) } == "rendered"
        read(mode, row, "none") { r, c -> r.readString(row, c) } == null

        where:
        mode << MODES
    }

    @Unroll
    void "reads bytes directly, or from a value the driver will not hand out as bytes, by #mode"() {
        given:
        byte[] bytes = [1, 2, 3] as byte[]
        def row = new StubRow([raw: bytes, buffer: ByteBuffer.wrap(bytes)])

        expect:
        Arrays.equals((byte[]) read(mode, row, "raw") { r, c -> r.readBytes(row, c) }, bytes)
        Arrays.equals((byte[]) read(mode, row, "buffer") { r, c -> r.readBytes(row, c) }, bytes)

        where:
        mode << MODES
    }

    @Unroll
    void "reads an integer from an integer, another number, or a convertible value, by #mode"() {
        given:
        def row = new StubRow([integer: 7, wider: 7L, text: "7", none: null])

        expect:
        read(mode, row, "integer") { r, c -> r.readDynamic(row, c, DataType.INTEGER) } == 7
        read(mode, row, "wider") { r, c -> r.readDynamic(row, c, DataType.INTEGER) } instanceof Integer
        read(mode, row, "wider") { r, c -> r.readDynamic(row, c, DataType.INTEGER) } == 7
        read(mode, row, "text") { r, c -> r.readDynamic(row, c, DataType.INTEGER) } == 7
        read(mode, row, "none") { r, c -> r.readDynamic(row, c, DataType.INTEGER) } == null

        where:
        mode << MODES
    }

    @Unroll
    void "getRequiredValue falls back from the typed value to the raw value, by #mode"() {
        given:
        def row = new StubRow([
                typed       : "direct",
                rawSameType : new Typed("raw", [(String): null]),
                rawConverted: new Typed(5, [(String): null]),
                rawNull     : new Typed(null, [(String): null]),
                incompatible: 9,
        ])

        expect: "the typed value when the driver hands it out"
        read(mode, row, "typed") { r, c -> r.getRequiredValue(row, c, String) } == "direct"

        and: "the raw value, converted if needed, when the typed value is null"
        read(mode, row, "rawSameType") { r, c -> r.getRequiredValue(row, c, String) } == "raw"
        read(mode, row, "rawConverted") { r, c -> r.getRequiredValue(row, c, String) } == "5"
        read(mode, row, "rawNull") { r, c -> r.getRequiredValue(row, c, String) } == null

        and: "the converted raw value when the driver rejects the type"
        read(mode, row, "incompatible") { r, c -> r.getRequiredValue(row, c, String) } == "9"

        where:
        mode << MODES
    }

    @Unroll
    void "getRequiredValue reports a column that cannot be read at all, by #mode"() {
        given:
        def row = new StubRow([broken: new Typed(1, [:], true)])

        when:
        read(mode, row, "broken") { r, c -> r.getRequiredValue(row, c, String) }

        then:
        def e = thrown(DataAccessException)
        e.message.contains(identifier)

        where:
        mode      | identifier
        "name"    | "name [broken]"
        "ordinal" | "index [0]"
    }

    void "the name reader retries a column in upper case, the way some drivers report it"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def row = new StubRow([TITLE: new Typed("Book", [(Object): null])])

        expect:
        reader.getRequiredValue(row, "title", Object) == "Book"
    }

    @Unroll
    void "readDynamic reads a #dataType column, by #mode"() {
        given:
        def row = new StubRow([value: stored])

        expect:
        read(mode, row, "value") { r, c -> r.readDynamic(row, c, dataType) } == expected

        where:
        [mode, entry] << [MODES, DYNAMIC_READS].combinations()
        dataType = entry[0]
        stored = entry[1]
        expected = entry[2]
    }

    void "uses the conversion service it is given, and the shared one otherwise"() {
        given:
        def conversionService = Mock(DataConversionService)
        def configured = new ColumnNameR2dbcResultReader(conversionService)

        expect:
        configured.getConversionService().is(conversionService)
        configured.getColumnIndexReader().getConversionService().is(conversionService)
        new ColumnNameR2dbcResultReader().getConversionService().is(ConversionService.SHARED)
    }

    void "next is not used for R2DBC rows"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def row = new StubRow([id: 1L])

        expect:
        !reader.next(row)
        !reader.getColumnIndexReader().next(row)
    }

    void "the name reader reports a column it cannot read in either case"() {
        given:
        def reader = new ColumnNameR2dbcResultReader()
        def row = new StubRow([TITLE: new Typed("Book", [(Object): null], false, true)])

        when: "the name is already upper case, so there is nothing to retry"
        reader.getRequiredValue(row, "MISSING", String)

        then:
        thrown(DataAccessException)

        when: "the typed read is null and the raw read is rejected, with nothing to retry"
        reader.getRequiredValue(row, "TITLE", Object)

        then:
        thrown(DataAccessException)

        when: "the raw read is rejected in lower case and again in upper case"
        reader.getRequiredValue(row, "title", Object)

        then: "the lower case failure is kept alongside the upper case one"
        def e = thrown(DataAccessException)
        e.cause.suppressed.length == 1
    }

    /**
     * Reads a column with the reader addressing it by name, or with the ordinal reader addressing it by the ordinal
     * resolved from the row metadata, so that every case runs against both readers.
     */
    private static Object read(String mode, Row row, String column, Closure call) {
        def reader = new ColumnNameR2dbcResultReader()
        if (mode == "name") {
            return call.call(reader, column)
        }
        return call.call(reader.getColumnIndexReader(), reader.findColumnIndex(row, column))
    }

    /**
     * A column whose typed read returns what {@code typed} maps the requested type to, null included. All of its
     * reads fail when it is {@code broken}, and only its raw read is rejected when it is {@code rawRejected}.
     */
    static class Typed {
        final Object raw
        final Map<Class, Object> typed
        final boolean broken
        final boolean rawRejected

        Typed(Object raw, Map<Class, Object> typed, boolean broken = false, boolean rawRejected = false) {
            this.raw = raw
            this.typed = typed
            this.broken = broken
            this.rawRejected = rawRejected
        }
    }

    static class StubClob implements Clob {
        final String text

        StubClob(String text) {
            this.text = text
        }

        @Override
        Publisher<CharSequence> stream() {
            return text == null ? Flux.empty() : Flux.just(text)
        }

        @Override
        Publisher<Void> discard() {
            return Mono.empty()
        }
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
        final List<String> names

        StubRowMetadata(List<String> names) {
            this.names = names
        }

        @Override
        ColumnMetadata getColumnMetadata(int index) {
            return new StubColumnMetadata(names[index])
        }

        @Override
        ColumnMetadata getColumnMetadata(String name) {
            return new StubColumnMetadata(name)
        }

        @Override
        List<? extends ColumnMetadata> getColumnMetadatas() {
            return names.collect { new StubColumnMetadata(it) }
        }
    }

    /**
     * A row that looks columns up by exact name, rejecting an unknown name or a value of another type with an
     * {@link IllegalArgumentException} the way drivers do.
     */
    static class StubRow implements Row {
        final Map<String, Object> columns
        final List<String> names
        final RowMetadata metadata
        final boolean metadataAvailable

        StubRow(Map<String, Object> columns, RowMetadata metadata = null, boolean metadataAvailable = true) {
            this.columns = columns
            this.names = new ArrayList<>(columns.keySet())
            this.metadata = metadata ?: new StubRowMetadata(this.names)
            this.metadataAvailable = metadataAvailable
        }

        @Override
        RowMetadata getMetadata() {
            if (!metadataAvailable) {
                throw new IllegalStateException("Row metadata is unavailable")
            }
            return metadata
        }

        @Override
        <T> T get(int index, Class<T> type) {
            return typedValue(columns.get(names[index]), type)
        }

        @Override
        <T> T get(String name, Class<T> type) {
            return typedValue(column(name), type)
        }

        @Override
        Object get(int index) {
            return rawValue(columns.get(names[index]))
        }

        @Override
        Object get(String name) {
            return rawValue(column(name))
        }

        private Object column(String name) {
            if (!columns.containsKey(name)) {
                throw new IllegalArgumentException("Unknown column: " + name)
            }
            return columns.get(name)
        }

        private static Object rawValue(Object value) {
            if (value instanceof Typed) {
                if (value.broken) {
                    throw new IllegalStateException("Unreadable column")
                }
                if (value.rawRejected) {
                    throw new IllegalArgumentException("Raw read rejected")
                }
                return value.raw
            }
            return value
        }

        private static <T> T typedValue(Object value, Class<T> type) {
            if (value instanceof Typed) {
                if (value.broken) {
                    throw new IllegalArgumentException("Unreadable column")
                }
                if (value.typed.containsKey(type)) {
                    return (T) value.typed.get(type)
                }
                value = value.raw
            }
            if (value == null) {
                return null
            }
            if (type.isInstance(value)) {
                return (T) value
            }
            throw new IllegalArgumentException("Cannot read " + value.getClass().simpleName + " as " + type.simpleName)
        }
    }
}
