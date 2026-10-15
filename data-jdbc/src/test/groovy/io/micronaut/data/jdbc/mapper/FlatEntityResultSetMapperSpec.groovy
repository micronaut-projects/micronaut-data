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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper
import io.micronaut.data.tck.entities.BasicTypes
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.EntityWithIdClass
import io.micronaut.data.tck.entities.Food
import io.micronaut.data.tck.entities.Person
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class FlatEntityResultSetMapperSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    DataConversionService conversionService = context.getBean(DataConversionService)

    @Shared
    @AutoCleanup
    Connection connection = DriverManager.getConnection("jdbc:h2:mem:flatmapper;DB_CLOSE_DELAY=-1")

    void "a plan exists for a flat entity and for nothing else"() {
        expect:
        FlatEntityMappingPlan.of(entity(FlatTypes)) != null
        FlatEntityMappingPlan.of(entity(FlatRecord)) != null
        FlatEntityMappingPlan.of(entity(Person)) != null
        // An association
        FlatEntityMappingPlan.of(entity(Book)) == null
        // Types read through the conversion service (char, URL, URI, CharSequence)
        FlatEntityMappingPlan.of(entity(BasicTypes)) == null
        // A composite identity
        FlatEntityMappingPlan.of(entity(EntityWithIdClass)) == null
        // An enum
        FlatEntityMappingPlan.of(entity(Food)) == null
    }

    void "the plan resolves the column of every property"() {
        when:
        def plan = FlatEntityMappingPlan.of(entity(FlatTypes))

        then:
        plan.columnNames.toList().containsAll(["id", "version", "primitive_integer", "wrapper_long", "renamed_column", "uuid"])
        plan.columnNames.length == 29
    }

    void "maps the same entity as the general mapper"() {
        given:
        def entity = entity(FlatTypes)
        def plan = FlatEntityMappingPlan.of(entity)
        createTable("flat_types", plan.columnNames.collect { "$it ${columnType(it)}" })
        execute("INSERT INTO flat_types (id, version, primitive_integer, primitive_long, primitive_boolean, primitive_short, primitive_double, primitive_float, primitive_byte, string, wrapper_integer, wrapper_long, wrapper_boolean, wrapper_short, wrapper_double, wrapper_float, wrapper_byte, big_decimal, big_integer, byte_array, date, local_date_time, zoned_date_time, offset_date_time, local_date, local_time, instant, uuid, renamed_column) VALUES " +
            "(1, 3, 11, 12, TRUE, 13, 1.5, 2.5, 14, 'one', 21, 22, FALSE, 23, 3.5, 4.5, 24, 123.45, 678, X'010203', DATE '2024-01-02', TIMESTAMP '2024-02-03 04:05:06', TIMESTAMP '2024-03-04 05:06:07', TIMESTAMP '2024-03-14 05:06:07', DATE '2024-04-05', TIMESTAMP '1970-01-01 06:07:08', TIMESTAMP '2024-05-06 07:08:09', 'c2b9f4a0-3c9e-4c6e-9a7c-6b1f1a2b3c4d', 'aliased')")
        execute("INSERT INTO flat_types (id, version, primitive_integer, primitive_long, primitive_boolean, primitive_short, primitive_double, primitive_float, primitive_byte, string, wrapper_integer, wrapper_long, wrapper_boolean, wrapper_short, wrapper_double, wrapper_float, wrapper_byte, big_decimal, big_integer, byte_array, date, local_date_time, zoned_date_time, offset_date_time, local_date, local_time, instant, uuid, renamed_column) VALUES " +
            "(2, NULL, 0, 0, FALSE, 0, 0, 0, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)")
        // Zero values in nullable columns, which the typed getters cannot tell from NULL without wasNull
        execute("INSERT INTO flat_types (id, version, primitive_integer, primitive_long, primitive_boolean, primitive_short, primitive_double, primitive_float, primitive_byte, string, wrapper_integer, wrapper_long, wrapper_boolean, wrapper_short, wrapper_double, wrapper_float, wrapper_byte, big_decimal, big_integer, byte_array, date, local_date_time, zoned_date_time, offset_date_time, local_date, local_time, instant, uuid, renamed_column) VALUES " +
            "(3, 0, 0, 0, FALSE, 0, 0, 0, 0, '', 0, 0, FALSE, 0, 0, 0, 0, 0, 0, X'', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '')")

        when:
        List<FlatTypes> flat = mapFlat(plan, "SELECT * FROM flat_types ORDER BY id")
        List<FlatTypes> general = mapGeneral(entity, "SELECT * FROM flat_types ORDER BY id")

        then:
        flat.size() == 3
        general.size() == 3
        (0..2).every { sameProperties(entity, flat[it], general[it]) }

        and: "the values are what the row holds"
        flat[0].id == 1L
        flat[0].version == 3L
        flat[0].primitiveInteger == 11
        flat[0].primitiveLong == 12L
        flat[0].primitiveBoolean
        flat[0].primitiveShort == 13 as short
        flat[0].primitiveDouble == 1.5d
        flat[0].primitiveFloat == 2.5f
        flat[0].primitiveByte == 14 as byte
        flat[0].string == "one"
        flat[0].wrapperInteger == 21
        flat[0].wrapperLong == 22L
        flat[0].wrapperBoolean == Boolean.FALSE
        flat[0].wrapperShort == 23 as short
        flat[0].wrapperDouble == 3.5d
        flat[0].wrapperFloat == 4.5f
        flat[0].wrapperByte == 24 as byte
        flat[0].bigDecimal == 123.45G
        flat[0].bigInteger == 678G
        flat[0].byteArray == [1, 2, 3] as byte[]
        flat[0].date == java.sql.Date.valueOf("2024-01-02")
        flat[0].localDateTime == LocalDateTime.of(2024, 2, 3, 4, 5, 6)
        flat[0].zonedDateTime == java.sql.Timestamp.valueOf("2024-03-04 05:06:07").toInstant().atZone(ZoneId.systemDefault())
        flat[0].offsetDateTime == java.sql.Timestamp.valueOf("2024-03-14 05:06:07").toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime()
        flat[0].localDate == LocalDate.of(2024, 4, 5)
        flat[0].localTime == LocalTime.of(6, 7, 8)
        flat[0].instant == java.sql.Timestamp.valueOf("2024-05-06 07:08:09").toInstant()
        flat[0].uuid == UUID.fromString("c2b9f4a0-3c9e-4c6e-9a7c-6b1f1a2b3c4d")
        flat[0].renamed == "aliased"

        and: "NULL leaves a wrapper null and a primitive at its default"
        flat[1].version == null
        flat[1].wrapperInteger == null
        flat[1].wrapperLong == null
        flat[1].wrapperBoolean == null
        flat[1].wrapperShort == null
        flat[1].wrapperDouble == null
        flat[1].wrapperFloat == null
        flat[1].wrapperByte == null
        flat[1].bigDecimal == null
        flat[1].byteArray == null
        flat[1].localDateTime == null
        flat[1].uuid == null
        flat[1].string == null
        flat[1].primitiveInteger == 0
        !flat[1].primitiveBoolean

        and: "zero is not NULL"
        flat[2].version == 0L
        flat[2].wrapperInteger == 0
        flat[2].wrapperLong == 0L
        flat[2].wrapperBoolean == Boolean.FALSE
        flat[2].wrapperShort == 0 as short
        flat[2].wrapperDouble == 0d
        flat[2].wrapperFloat == 0f
        flat[2].wrapperByte == 0 as byte
        flat[2].bigDecimal == 0G
        flat[2].byteArray == new byte[0]
        flat[2].string == ""

        cleanup:
        execute("DROP TABLE flat_types")
    }

    void "instantiates a record through its constructor"() {
        given:
        def entity = entity(FlatRecord)
        def plan = FlatEntityMappingPlan.of(entity)
        createTable("flat_record", ["id BIGINT", "name VARCHAR(255)", "quantity INT", "since DATE"])
        execute("INSERT INTO flat_record VALUES (1, 'first', 5, DATE '2024-06-07'), (2, 'second', NULL, NULL)")

        when:
        List<FlatRecord> flat = mapFlat(plan, "SELECT * FROM flat_record ORDER BY id")
        List<FlatRecord> general = mapGeneral(entity, "SELECT * FROM flat_record ORDER BY id")

        then:
        flat == general
        flat == [new FlatRecord(1L, "first", 5, LocalDate.of(2024, 6, 7)), new FlatRecord(2L, "second", null, null)]

        when: "a non-null constructor argument is NULL"
        execute("INSERT INTO flat_record VALUES (3, NULL, NULL, NULL)")
        mapFlat(plan, "SELECT * FROM flat_record WHERE id = 3")

        then:
        def e = thrown(DataAccessException)
        e.message.contains("Null value read for non-null constructor argument [name]")

        cleanup:
        execute("DROP TABLE flat_record")
    }

    void "a missing column is reported by name"() {
        given:
        def plan = FlatEntityMappingPlan.of(entity(FlatRecord))
        createTable("flat_record", ["id BIGINT", "name VARCHAR(255)"])
        execute("INSERT INTO flat_record VALUES (1, 'first')")

        when:
        mapFlat(plan, "SELECT * FROM flat_record")

        then:
        def e = thrown(DataAccessException)
        e.message.contains("[quantity]")

        cleanup:
        execute("DROP TABLE flat_record")
    }

    void "the load listener sees every entity"() {
        given:
        def plan = FlatEntityMappingPlan.of(entity(FlatRecord))
        createTable("flat_record", ["id BIGINT", "name VARCHAR(255)", "quantity INT", "since DATE"])
        execute("INSERT INTO flat_record VALUES (1, 'first', 5, NULL), (2, 'second', NULL, NULL)")
        def seen = []
        def mapper = new FlatEntityResultSetMapper<FlatRecord>(plan, conversionService, { e, o -> seen.add(o); new FlatRecord(o.id(), o.name().toUpperCase(), o.quantity(), o.since()) }, { throw new IllegalStateException() })

        when:
        def result = mapAll(mapper, "SELECT * FROM flat_record ORDER BY id")

        then:
        seen*.name() == ["first", "second"]
        result*.name() == ["FIRST", "SECOND"]

        cleanup:
        execute("DROP TABLE flat_record")
    }

    private <T> List<T> mapFlat(FlatEntityMappingPlan<T> plan, String sql) {
        mapAll(new FlatEntityResultSetMapper<T>(plan, conversionService, null, { throw new IllegalStateException("No general mapper needed") }), sql)
    }

    private <T> List<T> mapAll(FlatEntityResultSetMapper<T> mapper, String sql) {
        Statement statement = connection.createStatement()
        ResultSet rs = statement.executeQuery(sql)
        try {
            List<T> result = []
            while (rs.next()) {
                result.add(mapper.readEntity(rs))
            }
            return result
        } finally {
            rs.close()
            statement.close()
        }
    }

    private <T> List<T> mapGeneral(RuntimePersistentEntity<T> entity, String sql) {
        Statement statement = connection.createStatement()
        ResultSet rs = statement.executeQuery(sql)
        try {
            def mapper = new SqlResultEntityTypeMapper<ResultSet, T>(entity, new ColumnNameResultSetReader(conversionService), Set.of(), null, conversionService)
            List<T> result = []
            while (rs.next()) {
                result.add(mapper.readEntity(rs))
            }
            return result
        } finally {
            rs.close()
            statement.close()
        }
    }

    private static <T> boolean sameProperties(RuntimePersistentEntity<T> entity, T a, T b) {
        for (def property : [entity.identity] + entity.persistentProperties.toList()) {
            def x = property.property.get(a)
            def y = property.property.get(b)
            boolean same = x instanceof byte[] ? Arrays.equals(x as byte[], y as byte[]) : x == y
            if (!same) {
                throw new AssertionError("Property ${property.name} differs: flat=$x general=$y")
            }
        }
        return true
    }

    private static <T> RuntimePersistentEntity<T> entity(Class<T> type) {
        new RuntimePersistentEntity<T>(BeanIntrospection.getIntrospection(type))
    }

    private void createTable(String name, List<String> columns) {
        execute("DROP TABLE IF EXISTS $name")
        execute("CREATE TABLE $name (${columns.join(', ')})")
    }

    private void execute(String sql) {
        Statement statement = connection.createStatement()
        try {
            statement.execute(sql)
        } finally {
            statement.close()
        }
    }

    private static String columnType(String column) {
        switch (column) {
            case "id": case "version": case "primitive_long": case "wrapper_long": return "BIGINT"
            case "primitive_integer": case "wrapper_integer": return "INT"
            case "primitive_boolean": case "wrapper_boolean": return "BOOLEAN"
            case "primitive_short": case "wrapper_short": return "SMALLINT"
            case "primitive_double": case "wrapper_double": return "DOUBLE PRECISION"
            case "primitive_float": case "wrapper_float": return "REAL"
            case "primitive_byte": case "wrapper_byte": return "TINYINT"
            case "big_decimal": return "DECIMAL(10, 2)"
            case "big_integer": return "DECIMAL(20)"
            case "date": return "DATE"
            case "byte_array": return "BINARY VARYING(100)"
            // A LocalTime column is a TIMESTAMP, which is how the annotation processor types it
            case "local_date_time": case "zoned_date_time": case "offset_date_time": case "instant": case "local_time": return "TIMESTAMP"
            case "local_date": return "DATE"
            case "uuid": return "UUID"
            default: return "VARCHAR(255)"
        }
    }
}
