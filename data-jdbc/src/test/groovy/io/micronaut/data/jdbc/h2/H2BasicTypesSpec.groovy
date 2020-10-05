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
package io.micronaut.data.jdbc.h2


import io.micronaut.data.annotation.MappedProperty
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
@H2DBProperties
class H2BasicTypesSpec extends Specification {

    @Inject
    @Shared
    H2BasicTypeRepository repository

    @Inject
    @Shared
    DataSource dataSource


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
        "uuid"             | DataType.UUID
    }

    void "test save and retrieve basic types"() {
        when: "we save a new book"
        def book = repository.save(new BasicTypes())

        then: "The ID is assigned"
        book.myId != null

        when:"A book is found"
        def retrievedBook = repository.findByByteArray(book.byteArray)

        then:"The book is correct"
        retrievedBook.uuid == book.uuid
        retrievedBook.bigDecimal == book.bigDecimal
        retrievedBook.byteArray == book.byteArray
        retrievedBook.charSequence == book.charSequence
        retrievedBook.charset == book.charset
        retrievedBook.primitiveBoolean == book.primitiveBoolean
        retrievedBook.primitiveByte == book.primitiveByte
        retrievedBook.primitiveChar == book.primitiveChar
        retrievedBook.primitiveDouble == book.primitiveDouble
        retrievedBook.primitiveFloat == book.primitiveFloat
        retrievedBook.primitiveInteger == book.primitiveInteger
        retrievedBook.primitiveLong == book.primitiveLong
        retrievedBook.primitiveShort == book.primitiveShort
        retrievedBook.wrapperBoolean == book.wrapperBoolean
        retrievedBook.wrapperByte == book.wrapperByte
        retrievedBook.wrapperChar == book.wrapperChar
        retrievedBook.wrapperDouble == book.wrapperDouble
        retrievedBook.wrapperFloat == book.wrapperFloat
        retrievedBook.wrapperInteger == book.wrapperInteger
        retrievedBook.wrapperLong == book.wrapperLong
        retrievedBook.uri == book.uri
        retrievedBook.url == book.url
        retrievedBook.instant == book.instant
        retrievedBook.localDateTime == book.localDateTime
        retrievedBook.zonedDateTime == book.zonedDateTime
        retrievedBook.offsetDateTime == book.offsetDateTime
        retrievedBook.dateCreated == book.dateCreated
        retrievedBook.dateUpdated == book.dateUpdated
        // stored as a DATE type without time
//        retrievedBook.date == book.date

        when:"Updating a byte array"
        def changed = "changed byte".bytes
        repository.update(retrievedBook.myId, changed)

        then:
        repository.findByByteArray(changed) != null

    }
}
