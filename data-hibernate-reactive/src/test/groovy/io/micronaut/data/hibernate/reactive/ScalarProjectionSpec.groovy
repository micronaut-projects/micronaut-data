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
package io.micronaut.data.hibernate.reactive


import io.micronaut.data.hibernate.reactive.entities.Children
import io.micronaut.data.hibernate.reactive.entities.ChildrenId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(transactional = false, packages = "io.micronaut.data.hibernate.entities")
class ScalarProjectionSpec extends Specification implements PostgresHibernateReactiveProperties {
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
        ]).collectList().block()
    }

    void "Native queries with simple type result"() {
        when:
        def max = childrenRepository.getMaxNumber(1).block()

        then:
        max == 42
    }
}
