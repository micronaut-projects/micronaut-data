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

import io.micronaut.data.document.mongodb.entities.NestedIdChild
import io.micronaut.data.document.mongodb.entities.NestedIdEmbeddable
import io.micronaut.data.document.mongodb.entities.NestedIdParent
import io.micronaut.data.document.mongodb.repositories.NestedIdParentRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.types.ObjectId
import spock.lang.Specification

@MicronautTest(transactional = false)
class MongoNestedIdSpec extends Specification implements MongoTestPropertyProvider {

    @Inject
    NestedIdParentRepository repository

    void "a filter on the id of an embedded value finds the document"() {
        given:
        def code = new ObjectId().toHexString()
        def saved = repository.save(new NestedIdParent(
                null,
                new NestedIdChild(new ObjectId().toHexString(), "child"),
                [new NestedIdChild(new ObjectId().toHexString(), "item")],
                new NestedIdEmbeddable(code, "embeddable")
        ))

        when:
        def found = repository.findByEmbeddableCode(code)

        then:
        found.present
        found.get().id() == saved.id()
        found.get().embeddable() == saved.embeddable()

        cleanup:
        repository.deleteAll()
    }
}
