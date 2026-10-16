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
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.mapper.generated.Country
import io.micronaut.data.jdbc.mapper.generated.GeneratedTypes
import io.micronaut.data.jdbc.mapper.generated.GeoPoint
import io.micronaut.data.jdbc.mapper.generated.JsonCreatorTag
import io.micronaut.data.jdbc.mapper.generated.NoArgCreatorProbe
import io.micronaut.data.jdbc.mapper.generated.Place
import io.micronaut.data.jdbc.mapper.generated.Probe
import io.micronaut.data.jdbc.mapper.generated.Tag
import io.micronaut.data.jdbc.mapper.generated.Unsupported
import io.micronaut.data.jdbc.mapper.generated.Visit
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Order
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

/**
 * The row mappers the annotation processor generates: found by entity, reading every supported type, building
 * embedded values and association targets, through setters and constructors, to the values the general mapper reads.
 */
class GeneratedRowMapperSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    DataConversionService conversionService = context.getBean(DataConversionService)

    @Shared
    @AutoCleanup
    Connection connection = DriverManager.getConnection("jdbc:h2:mem:generatedmapper;DB_CLOSE_DELAY=-1")

    void "a mapper is generated for the entities it can read and registered as a service"() {
        expect:
        GeneratedRowMappers.find(GeneratedTypes).getClass().name == GeneratedTypes.name + '$JdbcRowMapper'
        GeneratedRowMappers.find(GeneratedTypes).entityType() == GeneratedTypes
        GeneratedRowMappers.find(Place) != null
        GeneratedRowMappers.find(Visit) != null
        GeneratedRowMappers.find(Country) != null
        GeneratedRowMappers.find(Book) != null

        and: "not for an entity with a JSON column, nor for one whose identity has no setter"
        GeneratedRowMappers.find(Unsupported) == null
        GeneratedRowMappers.find(Order) == null

        and: "the columns are the ones a query of the entity selects, embedded leaves (unprefixed unless the embedded property names a prefix) and foreign keys included"
        GeneratedRowMappers.find(Place).columnNames().toList() == ["id", "name", "name", "number", "other_name", "other_number", "lat", "lon", "country_id", "owner_id"]
        GeneratedRowMappers.find(Visit).columnNames().toList() == ["id", "name", "count", "since", "lat", "lon", "country_id"]
        GeneratedRowMappers.find(GeneratedTypes).columnNames().toList().containsAll(["id", "version", "primitive_char", "renamed_column", "with_default"])
    }

    void "reads every type to the value the general mapper reads"() {
        given:
        def entity = entity(GeneratedTypes)
        def plan = FlatEntityMappingPlan.of(entity)
        createTable("generated_types", plan.columnNames.collect { "$it ${columnType(it)}" })
        def columns = "(id, version, primitive_integer, primitive_long, primitive_boolean, primitive_short, primitive_double, primitive_float, primitive_byte, primitive_char, string, wrapper_integer, wrapper_long, wrapper_boolean, wrapper_short, wrapper_double, wrapper_float, wrapper_byte, wrapper_char, big_decimal, big_integer, byte_array, date, sql_date, timestamp, local_date_time, zoned_date_time, offset_date_time, local_date, local_time, instant, uuid, renamed_column, suit, with_default)"
        execute("INSERT INTO generated_types $columns VALUES " +
            "(1, 3, 11, 12, TRUE, 13, 1.5, 2.5, 14, 'x', 'one', 21, 22, FALSE, 23, 3.5, 4.5, 24, 'y', 123.45, 678, X'010203', DATE '2024-01-02', DATE '2024-01-03', TIMESTAMP '2024-01-04 01:02:03', TIMESTAMP '2024-02-03 04:05:06', TIMESTAMP '2024-03-04 05:06:07', TIMESTAMP '2024-03-14 05:06:07', DATE '2024-04-05', TIMESTAMP '1970-01-01 06:07:08', TIMESTAMP '2024-05-06 07:08:09', 'c2b9f4a0-3c9e-4c6e-9a7c-6b1f1a2b3c4d', 'aliased', 'HEARTS', 9)")
        execute("INSERT INTO generated_types $columns VALUES " +
            "(2, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)")
        // Zero values in nullable columns, which the typed getters cannot tell from NULL without wasNull
        execute("INSERT INTO generated_types $columns VALUES " +
            "(3, 0, 0, 0, FALSE, 0, 0, 0, 0, '', '', 0, 0, FALSE, 0, 0, 0, 0, '', 0, 0, X'', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '', 'SPADES', 0)")

        expect:
        plan.generatedMapper != null

        when:
        List<GeneratedTypes> generated = mapGenerated(plan, "SELECT * FROM generated_types ORDER BY id")
        List<GeneratedTypes> general = mapGeneral(entity, "SELECT * FROM generated_types ORDER BY id")

        then:
        generated.size() == 3
        (0..2).every { sameProperties(entity, generated[it], general[it]) }

        and: "the values are what the row holds"
        generated[0].id == 1L
        generated[0].version == 3L
        generated[0].primitiveInteger == 11
        generated[0].primitiveLong == 12L
        generated[0].primitiveBoolean
        generated[0].primitiveShort == 13 as short
        generated[0].primitiveDouble == 1.5d
        generated[0].primitiveFloat == 2.5f
        generated[0].primitiveByte == 14 as byte
        generated[0].primitiveChar == 'x' as char
        generated[0].string == "one"
        generated[0].wrapperInteger == 21
        generated[0].wrapperLong == 22L
        generated[0].wrapperBoolean == Boolean.FALSE
        generated[0].wrapperShort == 23 as short
        generated[0].wrapperDouble == 3.5d
        generated[0].wrapperFloat == 4.5f
        generated[0].wrapperByte == 24 as byte
        generated[0].wrapperChar == 'y' as Character
        generated[0].bigDecimal == 123.45G
        generated[0].bigInteger == 678G
        generated[0].byteArray == [1, 2, 3] as byte[]
        generated[0].date == java.sql.Date.valueOf("2024-01-02")
        generated[0].sqlDate == java.sql.Date.valueOf("2024-01-03")
        generated[0].timestamp == java.sql.Timestamp.valueOf("2024-01-04 01:02:03")
        generated[0].localDateTime == LocalDateTime.of(2024, 2, 3, 4, 5, 6)
        generated[0].zonedDateTime == java.sql.Timestamp.valueOf("2024-03-04 05:06:07").toInstant().atZone(ZoneId.systemDefault())
        generated[0].offsetDateTime == java.sql.Timestamp.valueOf("2024-03-14 05:06:07").toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime()
        generated[0].localDate == LocalDate.of(2024, 4, 5)
        generated[0].localTime == LocalTime.of(6, 7, 8)
        generated[0].instant == java.sql.Timestamp.valueOf("2024-05-06 07:08:09").toInstant()
        generated[0].uuid == UUID.fromString("c2b9f4a0-3c9e-4c6e-9a7c-6b1f1a2b3c4d")
        generated[0].renamed == "aliased"
        generated[0].suit == GeneratedTypes.Suit.HEARTS
        generated[0].withDefault == 9

        and: "NULL leaves a wrapper null and a primitive at its initial value"
        generated[1].version == null
        generated[1].wrapperInteger == null
        generated[1].wrapperLong == null
        generated[1].wrapperBoolean == null
        generated[1].wrapperChar == null
        generated[1].bigDecimal == null
        generated[1].byteArray == null
        generated[1].localDateTime == null
        generated[1].uuid == null
        generated[1].string == null
        generated[1].suit == null
        generated[1].primitiveInteger == 0
        generated[1].primitiveLong == 0L
        generated[1].primitiveDouble == 0d
        generated[1].primitiveChar == 0 as char
        generated[1].withDefault == 7
        !generated[1].primitiveBoolean

        and: "zero is not NULL"
        generated[2].version == 0L
        generated[2].wrapperInteger == 0
        generated[2].wrapperLong == 0L
        generated[2].wrapperBoolean == Boolean.FALSE
        generated[2].wrapperShort == 0 as short
        generated[2].wrapperDouble == 0d
        generated[2].wrapperFloat == 0f
        generated[2].wrapperByte == 0 as byte
        generated[2].wrapperChar == 0 as Character
        generated[2].bigDecimal == 0G
        generated[2].byteArray == new byte[0]
        generated[2].string == ""
        generated[2].suit == GeneratedTypes.Suit.SPADES
        generated[2].withDefault == 0

        when: "an enum name only the conversion service resolves"
        execute("INSERT INTO generated_types (id, suit) VALUES (4, 'spades')")
        List<GeneratedTypes> lower = mapGenerated(plan, "SELECT * FROM generated_types WHERE id = 4")

        then:
        lower[0].suit == GeneratedTypes.Suit.SPADES
        lower[0].suit == mapGeneral(entity, "SELECT * FROM generated_types WHERE id = 4")[0].suit

        when: "a value no converter accepts fails the way the general mapper fails"
        execute("INSERT INTO generated_types (id, suit) VALUES (5, '')")
        mapGenerated(plan, "SELECT * FROM generated_types WHERE id = 5")

        then:
        def e = thrown(DataAccessException)
        e.message.contains("Cannot convert type [class java.lang.String] with value []")

        cleanup:
        execute("DROP TABLE generated_types")
    }

    void "builds embedded values and association targets through setters"() {
        given:
        def entity = entity(Place)
        def plan = FlatEntityMappingPlan.of(entity)
        // The entity's own name and the street's share a column name, as they would in the entity's table: the
        // street's name is read from the second "name" column, which is what the general mapper does as well
        createTable("generated_place", ["id BIGINT", "name VARCHAR(255)", "number INT", "other_name VARCHAR(255)", "other_number INT", "lat DOUBLE", "lon DOUBLE", "country_id BIGINT", "owner_id BIGINT"])
        execute("INSERT INTO generated_place VALUES (1, 'full', 12, 'Side', 3, 1.5, 2.5, 7, 8)")
        execute("INSERT INTO generated_place VALUES (2, 'empty', NULL, NULL, NULL, NULL, NULL, NULL, NULL)")
        execute("INSERT INTO generated_place VALUES (3, 'partial', 4, 'Only name', NULL, NULL, NULL, NULL, NULL)")

        when:
        List<Place> generated = mapGenerated(plan, "SELECT * FROM generated_place ORDER BY id")
        List<Place> general = mapGeneral(entity, "SELECT * FROM generated_place ORDER BY id")

        then: "every value, embedded or associated, is the general mapper's"
        (0..2).every { samePlace(generated[it], general[it]) }

        and:
        generated[0].street.name == "full"
        generated[0].street.number == 12
        generated[0].otherStreet.name == "Side"
        generated[0].otherStreet.number == 3
        generated[0].point == new GeoPoint(1.5d, 2.5d)
        generated[0].country.id() == 7L
        generated[0].owner.id == 8L
        generated[0].owner.string == null

        and: "a required embedded value exists without a column value, a nullable one and an association do not"
        generated[1].street != null
        generated[1].street.name == "empty"
        generated[1].street.number == null
        generated[1].otherStreet == null
        generated[1].point == null
        generated[1].country == null
        generated[1].owner == null

        and: "one column value is enough for a nullable embedded value"
        generated[2].street.number == 4
        generated[2].otherStreet.name == "Only name"
        generated[2].otherStreet.number == null

        cleanup:
        execute("DROP TABLE generated_place")
    }

    void "builds a record through its constructor"() {
        given:
        def entity = entity(Visit)
        def plan = FlatEntityMappingPlan.of(entity)
        createTable("generated_visit", ["id BIGINT", "name VARCHAR(255)", "count INT", "since DATE", "lat DOUBLE", "lon DOUBLE", "country_id BIGINT"])
        execute("INSERT INTO generated_visit VALUES (1, 'first', 5, DATE '2024-06-07', 1.5, 2.5, 7), (2, 'second', 0, NULL, NULL, NULL, NULL)")

        when:
        List<Visit> generated = mapGenerated(plan, "SELECT * FROM generated_visit ORDER BY id")
        List<Visit> general = mapGeneral(entity, "SELECT * FROM generated_visit ORDER BY id")

        then:
        generated == general
        generated == [new Visit(1L, "first", 5, LocalDate.of(2024, 6, 7), new GeoPoint(1.5d, 2.5d), new Country(7L)), new Visit(2L, "second", 0, null, null, null)]

        when: "a non-null constructor argument is NULL"
        execute("INSERT INTO generated_visit VALUES (3, NULL, 1, NULL, NULL, NULL, NULL)")
        mapGenerated(plan, "SELECT * FROM generated_visit WHERE id = 3")

        then:
        def e = thrown(DataAccessException)
        e.message.contains("Null value read for non-null constructor argument [name]")

        when: "a primitive constructor argument is NULL"
        execute("INSERT INTO generated_visit VALUES (4, 'fourth', NULL, NULL, NULL, NULL, NULL)")
        mapGenerated(plan, "SELECT * FROM generated_visit WHERE id = 4")

        then:
        e = thrown(DataAccessException)
        e.message.contains("Null value read for non-null constructor argument [count]")

        cleanup:
        execute("DROP TABLE generated_visit")
    }

    void "an entity with a static creator is built through it, as the introspection builds it"() {
        given:
        createTable("generated_probe", ["id BIGINT", "name VARCHAR(255)", "active BOOLEAN"])
        execute("INSERT INTO generated_probe VALUES (1, 'probe', FALSE)")
        createTable("generated_tag", ["id BIGINT", "name VARCHAR(255)"])
        execute("INSERT INTO generated_tag VALUES (1, 'lower')")
        createTable("generated_json_creator_tag", ["id BIGINT", "name VARCHAR(255)"])
        execute("INSERT INTO generated_json_creator_tag VALUES (1, 'lower')")
        createTable("generated_no_arg_creator_probe", ["id BIGINT", "name VARCHAR(255)", "created BOOLEAN"])
        execute("INSERT INTO generated_no_arg_creator_probe VALUES (1, 'probe', FALSE)")

        when:
        List<Probe> probes = mapGenerated(FlatEntityMappingPlan.of(entity(Probe)), "SELECT * FROM generated_probe")
        List<Tag> tags = mapGenerated(FlatEntityMappingPlan.of(entity(Tag)), "SELECT * FROM generated_tag")

        then: "the creator's parameters come from the columns, the rest from the setters"
        probes[0].id == 1L
        probes[0].name == "probe"
        !probes[0].active
        def general = mapGeneral(entity(Probe), "SELECT * FROM generated_probe")
        probes*.id == general*.id && probes*.name == general*.name && probes*.active == general*.active

        and: "the record's creator normalizes the component, which the canonical constructor would not"
        tags == [new Tag(1L, "LOWER")]
        tags == mapGeneral(entity(Tag), "SELECT * FROM generated_tag")

        when: "the creator is one by stereotype only"
        List<JsonCreatorTag> jsonTags = mapGenerated(FlatEntityMappingPlan.of(entity(JsonCreatorTag)), "SELECT * FROM generated_json_creator_tag")

        then:
        jsonTags == [new JsonCreatorTag(1L, "LOWER")]
        jsonTags == mapGeneral(entity(JsonCreatorTag), "SELECT * FROM generated_json_creator_tag")

        when: "a parameterless static creator is the only way to instantiate the entity"
        List<NoArgCreatorProbe> created = mapGenerated(FlatEntityMappingPlan.of(entity(NoArgCreatorProbe)), "SELECT * FROM generated_no_arg_creator_probe")

        then: "it is called, then the setters"
        created[0].id == 1L
        created[0].name == "probe"
        created[0].created
        created[0].created == mapGeneral(entity(NoArgCreatorProbe), "SELECT * FROM generated_no_arg_creator_probe")[0].created

        cleanup:
        execute("DROP TABLE generated_probe")
        execute("DROP TABLE generated_tag")
        execute("DROP TABLE generated_no_arg_creator_probe")
        execute("DROP TABLE generated_json_creator_tag")
    }

    void "the generated mapper is used directly with resolved ordinals"() {
        given:
        def mapper = GeneratedRowMappers.find(Country)
        createTable("generated_country", ["extra VARCHAR(10)", "id BIGINT"])
        execute("INSERT INTO generated_country VALUES ('x', 42)")
        Statement statement = connection.createStatement()
        ResultSet rs = statement.executeQuery("SELECT * FROM generated_country")

        when:
        rs.next()
        Country country = mapper.map(rs, [2] as int[], conversionService)

        then:
        country.id() == 42L

        cleanup:
        rs.close()
        statement.close()
        execute("DROP TABLE generated_country")
    }

    private static boolean samePlace(Place a, Place b) {
        assert a.id == b.id
        assert a.name == b.name
        assert a.street?.name == b.street?.name
        assert a.street?.number == b.street?.number
        assert (a.street == null) == (b.street == null)
        assert a.otherStreet?.name == b.otherStreet?.name
        assert a.otherStreet?.number == b.otherStreet?.number
        assert (a.otherStreet == null) == (b.otherStreet == null)
        assert a.point == b.point
        assert a.country == b.country
        assert a.owner?.id == b.owner?.id
        assert (a.owner == null) == (b.owner == null)
        return true
    }

    private <T> List<T> mapGenerated(FlatEntityMappingPlan<T> plan, String sql) {
        assert plan.generatedMapper != null
        Statement statement = connection.createStatement()
        ResultSet rs = statement.executeQuery(sql)
        try {
            def mapper = new FlatEntityResultSetMapper<T>(plan, conversionService, null, { throw new IllegalStateException("No general mapper needed") })
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
                throw new AssertionError("Property ${property.name} differs: generated=$x general=$y")
            }
        }
        return true
    }

    private <T> RuntimePersistentEntity<T> entity(Class<T> type) {
        new RuntimePersistentEntity<T>(type)
    }

    private void createTable(String name, List<String> columns) {
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
            case "primitive_integer": case "wrapper_integer": case "with_default": return "INT"
            case "primitive_boolean": case "wrapper_boolean": return "BOOLEAN"
            case "primitive_short": case "wrapper_short": return "SMALLINT"
            case "primitive_byte": case "wrapper_byte": return "TINYINT"
            case "primitive_double": case "wrapper_double": return "DOUBLE"
            case "primitive_float": case "wrapper_float": return "REAL"
            case "primitive_char": case "wrapper_char": return "VARCHAR(1)"
            case "big_decimal": return "DECIMAL(10, 2)"
            case "big_integer": return "DECIMAL(20)"
            case "byte_array": return "VARBINARY(16)"
            case "date": case "sql_date": case "local_date": return "DATE"
            case "timestamp": case "local_date_time": case "zoned_date_time": case "offset_date_time": case "local_time": case "instant": return "TIMESTAMP"
            case "uuid": return "UUID"
            default: return "VARCHAR(255)"
        }
    }
}
