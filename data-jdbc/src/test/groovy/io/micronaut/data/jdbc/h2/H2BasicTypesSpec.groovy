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
import io.micronaut.data.model.DataType
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.tck.entities.BasicTypes
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Issue
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
    H2BasicTypesRepository repository

    @Inject
    @Shared
    DataSource dataSource


    @Issue("https://github.com/micronaut-projects/micronaut-data/issues/769")
    void 'test query that returns null'() {
        expect:
            !repository.somethingThatMightSometimesReturnNull().isPresent()
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
            "uuid"             | DataType.UUID
    }
}
