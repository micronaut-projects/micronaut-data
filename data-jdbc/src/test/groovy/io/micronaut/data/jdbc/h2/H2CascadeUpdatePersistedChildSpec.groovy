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
package io.micronaut.data.jdbc.h2

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.jspecify.annotations.Nullable
import spock.lang.Specification

@MicronautTest(transactional = false)
class H2CascadeUpdatePersistedChildSpec extends Specification implements H2TestPropertyProvider {

    @Inject
    JdbcCascadeUpdateParentRepository parentRepository

    void "test cascading update keeps the children that were already cascaded"() {
        given:
            def children = []
            def parent = new JdbcCascadeUpdateParent(name: "parent", children: children)
            children.add(new JdbcCascadeUpdateChild(name: "A", parent: parent))
            children.add(new JdbcCascadeUpdateChild(name: "B", parent: parent))
            parent = parentRepository.save(parent)

        when: "a child of the collection is also cascaded by a to-one association"
            def favourite = parent.children.find { it.name == "A" }
            parent.favourite = favourite
            parent.children.forEach { it.name = it.name + " mod" }
            def updated = parentRepository.update(parent)

        then:
            updated.favourite.is(favourite)
            updated.children.size() == 2
            updated.children*.name.toSorted() == ["A mod", "B mod"]
            updated.children.any { it.is(favourite) }

        when:
            def found = parentRepository.findById(parent.id).get()

        then:
            found.favourite.id == favourite.id
            found.children*.name.toSorted() == ["A mod", "B mod"]
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface JdbcCascadeUpdateParentRepository extends CrudRepository<JdbcCascadeUpdateParent, Long> {

    @Join(value = "children", type = Join.Type.FETCH)
    @Join(value = "favourite", type = Join.Type.FETCH)
    @Override
    Optional<JdbcCascadeUpdateParent> findById(Long id)
}

@MappedEntity("jdbc_cascade_upd_parent")
class JdbcCascadeUpdateParent {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
    @Nullable
    JdbcCascadeUpdateChild favourite
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent", cascade = Relation.Cascade.ALL)
    List<JdbcCascadeUpdateChild> children
}

@MappedEntity("jdbc_cascade_upd_child")
class JdbcCascadeUpdateChild {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    JdbcCascadeUpdateParent parent
}
