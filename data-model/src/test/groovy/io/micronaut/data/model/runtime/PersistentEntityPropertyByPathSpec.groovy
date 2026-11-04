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

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import spock.lang.Specification
import spock.lang.Unroll

class PersistentEntityPropertyByPathSpec extends Specification {

    @Unroll
    void "test property by path #path of an entity with an identity"() {
        given:
        def entity = new RuntimePersistentEntity(PathOrder)

        when:
        def property = entity.getPropertyByPath(path)

        then:
        property.map { it.name } == Optional.ofNullable(expected)

        where:
        path                    | expected
        "id"                    | "id"
        "details.note"          | "note"
        "details.nested.value"  | "value"
        "details.unknown"       | null
        "details.nested.other"  | null
        "details.unknown.value" | null
        "unknown.note"          | null
    }

    @Unroll
    void "test property by path #path of an entity without an identity"() {
        given:
        def entity = new RuntimePersistentEntity(PathView)

        when:
        def property = entity.getPropertyByPath(path)

        then:
        property.map { it.name } == Optional.ofNullable(expected)

        where:
        path                    | expected
        "name"                  | "name"
        "details.note"          | "note"
        "details.unknown"       | null
        "unknown.name"          | null
        "details.unknown.value" | null
    }
}

@MappedEntity
class PathOrder {
    @Id
    Long id
    @Relation(Relation.Kind.EMBEDDED)
    PathDetails details
}

@MappedEntity
class PathView {
    String name
    @Relation(Relation.Kind.EMBEDDED)
    PathDetails details
}

@Embeddable
class PathDetails {
    String note
    @Relation(Relation.Kind.EMBEDDED)
    PathNested nested
}

@Embeddable
class PathNested {
    String value
}
