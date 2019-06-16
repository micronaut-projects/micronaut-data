package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.jdbc.BasicTypeRepository
import io.micronaut.data.jdbc.BasicTypes
import io.micronaut.data.model.DataType
import io.micronaut.data.model.PersistentEntity
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Inject
import javax.sql.DataSource

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
class H2BasicTypesSpec extends Specification {

    @Inject
    @Shared
    BasicTypeRepository repository

    @Inject
    @Shared
    DataSource dataSource

    void setupSpec() {
        def conn = dataSource.getConnection()
        try {
            conn.prepareStatement('''
create table basic_types (
    id bigint auto_increment, 
    primary key (id),
    primitive_integer integer not null,
    primitive_long bigint not null,
    primitive_boolean bit not null,
    primitive_char integer not null,
    primitive_short smallint not null,
    primitive_double double not null,
    primitive_float float not null,
    primitive_byte tinyint not null,
    string varchar(255) not null, 
    char_sequence varchar(255) not null, 
    wrapper_integer integer not null,
    wrapper_long bigint not null,
    wrapper_boolean bit not null,
    wrapper_char integer not null,
    wrapper_short smallint not null,
    wrapper_double double not null,
    wrapper_float float not null,
    wrapper_byte tinyint not null,
    `url` varchar(255) not null,
    `uri` varchar(255) not null,
    byte_array BINARY(1000) not null,
    `date` DATE not null,
    `instant` timestamp not null,
    local_date_time timestamp not null,
    `uuid` varchar(255) not null,
    big_decimal DECIMAL not null,
    time_zone varchar(255) not null,
    charset varchar(255) not null
)
''').execute()
        } finally {
            conn.close()
        }
    }

    @Unroll
    void 'test basic type mapping for property #property'() {
        given:
        PersistentEntity entity = PersistentEntity.of(BasicTypes)
        def prop = entity.getPropertyByName(property)

        expect:
        prop.getAnnotation(MappedProperty)
                .enumValue("type", DataType)
                .get() == type

        where:
        property           | type
        "primitiveInteger" | DataType.INTEGER
        "wrapperInteger"   | DataType.INTEGER
        "primitiveBoolean" | DataType.BOOLEAN
        "wrapperBoolean"   | DataType.BOOLEAN
        "primitiveShort"   | DataType.SHORT
        "wrapperShort"     | DataType.SHORT
        "primitiveLong"    | DataType.LONG
        "wrapperLong"      | DataType.LONG
        "primitiveDouble"  | DataType.DOUBLE
        "wrapperDouble"    | DataType.DOUBLE
        "uuid"             | DataType.STRING
    }

    void "test save and retrieve basic types"() {
        when: "we save a new book"
        def book = repository.save(new BasicTypes())

        then: "The ID is assigned"
        book.myId != null

    }
}
