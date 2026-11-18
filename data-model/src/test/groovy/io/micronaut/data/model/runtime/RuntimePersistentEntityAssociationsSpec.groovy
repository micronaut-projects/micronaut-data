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
package io.micronaut.data.model.runtime

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.Association
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class RuntimePersistentEntityAssociationsSpec extends Specification {

    void "the associations are the association properties in property order"() {
        given:
            def entity = new RuntimePersistentEntity(AssociationOwner)
        expect:
            entity.associations*.name == ["details", "target"]
            entity.associations as List == entity.persistentProperties.findAll { it instanceof Association }
    }

    void "the same list is returned on every call and can't be modified"() {
        given:
            def entity = new RuntimePersistentEntity(AssociationOwner)
        when:
            def first = entity.associations
        then:
            entity.associations.is(first)
        when:
            first.add(first[0])
        then:
            thrown(UnsupportedOperationException)
    }

    void "an entity without associations has none"() {
        expect:
            new RuntimePersistentEntity(AssociationTarget).associations.isEmpty()
    }

    void "threads reading the associations of a new entity at the same time see the same contents"() {
        given:
            def entity = new RuntimePersistentEntity(AssociationOwner)
            def executor = Executors.newFixedThreadPool(8)
            def start = new CountDownLatch(1)
        when:
            def futures = (0..<8).collect {
                executor.submit({
                    start.await()
                    entity.associations*.name
                } as Callable<List<String>>)
            }
            start.countDown()
        then:
            futures.every { it.get() == ["details", "target"] }
        cleanup:
            executor.shutdown()
    }
}

@MappedEntity
class AssociationOwner {
    @Id
    Long id
    @Relation(Relation.Kind.EMBEDDED)
    PathDetails details
    String name
    @Relation(Relation.Kind.MANY_TO_ONE)
    AssociationTarget target
}

@MappedEntity
class AssociationTarget {
    @Id
    Long id
    String label
}
