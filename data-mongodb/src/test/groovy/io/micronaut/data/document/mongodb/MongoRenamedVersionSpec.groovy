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
package io.micronaut.data.document.mongodb

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Version
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The optimistic locking filter must use the stored field name of a renamed version property.
 */
class MongoRenamedVersionSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    RenamedVersionRepository repository = applicationContext.getBean(RenamedVersionRepository)

    @Shared
    RenamedVersionReactiveRepository reactiveRepository = applicationContext.getBean(RenamedVersionReactiveRepository)

    def cleanup() {
        repository.deleteAll()
    }

    void "update with the current version succeeds and increments it"() {
        given:
            def doc = repository.save(new RenamedVersionDoc(name: "a"))
        when:
            doc.name = "b"
            def updated = repository.update(doc)
        then:
            updated.version == 1
            repository.findById(doc.id).get().name == "b"
    }

    void "update with a stale version fails"() {
        given:
            def doc = repository.save(new RenamedVersionDoc(name: "a"))
            def stale = new RenamedVersionDoc(id: doc.id, name: "stale", version: 5)
        when:
            repository.update(stale)
        then:
            thrown(OptimisticLockException)
            repository.findById(doc.id).get().name == "a"
    }

    void "delete with a stale version fails"() {
        given:
            def doc = repository.save(new RenamedVersionDoc(name: "a"))
            def stale = new RenamedVersionDoc(id: doc.id, name: "a", version: 5)
        when:
            repository.delete(stale)
        then:
            thrown(OptimisticLockException)
            repository.findById(doc.id).present
    }

    void "bulk update with a stale version fails"() {
        given:
            def doc = repository.save(new RenamedVersionDoc(name: "a"))
            def stale = new RenamedVersionDoc(id: doc.id, name: "stale", version: 5)
        when:
            repository.updateAll([stale])
        then:
            thrown(OptimisticLockException)
            repository.findById(doc.id).get().name == "a"
    }

    void "bulk delete with a stale version fails"() {
        given:
            def doc = repository.save(new RenamedVersionDoc(name: "a"))
            def stale = new RenamedVersionDoc(id: doc.id, name: "a", version: 5)
        when:
            repository.deleteAll([stale])
        then:
            thrown(OptimisticLockException)
            repository.findById(doc.id).present
    }

    void "reactive update with a stale version fails"() {
        given:
            def doc = repository.save(new RenamedVersionDoc(name: "a"))
            def stale = new RenamedVersionDoc(id: doc.id, name: "stale", version: 5)
        when:
            reactiveRepository.update(stale).block()
        then:
            thrown(OptimisticLockException)
            repository.findById(doc.id).get().name == "a"
    }

    void "reactive delete with a stale version fails"() {
        given:
            def doc = repository.save(new RenamedVersionDoc(name: "a"))
            def stale = new RenamedVersionDoc(id: doc.id, name: "a", version: 5)
        when:
            reactiveRepository.delete(stale).block()
        then:
            thrown(OptimisticLockException)
            repository.findById(doc.id).present
    }
}

@MongoRepository
interface RenamedVersionRepository extends CrudRepository<RenamedVersionDoc, String> {
}

@MongoRepository
interface RenamedVersionReactiveRepository extends ReactorCrudRepository<RenamedVersionDoc, String> {
}

@MappedEntity
class RenamedVersionDoc {
    @Id
    @GeneratedValue
    String id

    String name

    @Version
    @MappedProperty("rev")
    Integer version
}
