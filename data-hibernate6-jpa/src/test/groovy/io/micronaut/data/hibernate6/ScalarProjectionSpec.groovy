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
package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate6.entities.Children
import io.micronaut.data.hibernate6.entities.ChildrenId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(packages = "io.micronaut.data.hibernate6.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class ScalarProjectionSpec extends Specification {
    @Shared
    @Inject
    ChildrenRepository childrenRepository

    void setupSpec() {
        childrenRepository.saveAll([
                new Children(new ChildrenId(1, 1)),
                new Children(new ChildrenId(1, 2)),
                new Children(new ChildrenId(1, 3)),
                new Children(new ChildrenId(1, 42)),
                new Children(new ChildrenId(2, 1)),
                new Children(new ChildrenId(2, 2))
        ])
    }

    void "Native queries with simple type result"() {
        when:
        def max = childrenRepository.getMaxNumber(1)

        then:
        max == 42
    }
}
