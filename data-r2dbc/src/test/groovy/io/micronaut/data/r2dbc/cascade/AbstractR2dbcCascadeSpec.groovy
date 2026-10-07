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
package io.micronaut.data.r2dbc.cascade

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.event.PostPersist
import io.micronaut.data.annotation.event.PrePersist
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import jakarta.inject.Inject
import org.jspecify.annotations.Nullable
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

abstract class AbstractR2dbcCascadeSpec extends Specification {

    private static final AtomicLong ID_SEQUENCE = new AtomicLong()

    @Inject
    ApplicationContext context

    abstract CascadeUpdateParentRepository getParentRepository()

    abstract CascadeGeneratedParentRepository getGeneratedParentRepository()

    abstract CascadeAssignedParentRepository getAssignedParentRepository()

    /**
     * @return Whether the dialect batches the insert of entities with a generated id
     */
    abstract boolean batchesInsertOfGeneratedIds()

    void setup() {
        CascadeEvents.EVENTS.clear()
    }

    void "test cascading update keeps the children that were already cascaded"() {
        given:
            def children = []
            def parent = new CascadeUpdateParent(name: "parent", children: children)
            children.add(new CascadeUpdateChild(name: "A", parent: parent))
            children.add(new CascadeUpdateChild(name: "B", parent: parent))
            parent = parentRepository.save(parent).block()

        when: "a child of the collection is also cascaded by a to-one association"
            def favourite = parent.children.find { it.name == "A" }
            parent.favourite = favourite
            parent.children.forEach { it.name = it.name + " mod" }
            def updated = parentRepository.update(parent).block()

        then:
            updated.favourite.is(favourite)
            updated.children.size() == 2
            updated.children*.name.toSorted() == ["A mod", "B mod"]
            updated.children.any { it.is(favourite) }

        when:
            def found = parentRepository.findById(parent.id).block()

        then:
            found.favourite.id == favourite.id
            found.children*.name.toSorted() == ["A mod", "B mod"]
    }

    void "test cascading persist batches the children with an assigned id of a parent with a generated id"() {
        given:
            def parent = new CascadeGeneratedParent(name: "parent")
            parent.children = [
                    new CascadeAssignedChild(id: ID_SEQUENCE.incrementAndGet(), name: "A", parent: parent),
                    new CascadeAssignedChild(id: ID_SEQUENCE.incrementAndGet(), name: "B", parent: parent)
            ]

        when:
            parent = generatedParentRepository.save(parent).block()

        then: "the children are inserted in a batch, every dialect batches entities with an assigned id"
            CascadeEvents.EVENTS == persistEvents(true, "A", "B")

        when:
            def found = generatedParentRepository.findById(parent.id).block()

        then:
            found.children*.name.toSorted() == ["A", "B"]
    }

    void "test cascading persist batches the children with a generated id of a parent with an assigned id only if the dialect supports it"() {
        given:
            def parent = new CascadeAssignedParent(id: ID_SEQUENCE.incrementAndGet(), name: "parent")
            parent.children = [
                    new CascadeGeneratedChild(name: "A", parent: parent),
                    new CascadeGeneratedChild(name: "B", parent: parent)
            ]

        when:
            parent = assignedParentRepository.save(parent).block()

        then:
            CascadeEvents.EVENTS == persistEvents(batchesInsertOfGeneratedIds(), "A", "B")
            parent.children.every { it.id != null }

        when:
            def found = assignedParentRepository.findById(parent.id).block()

        then:
            found.children*.name.toSorted() == ["A", "B"]
            found.children*.id.toSorted() == parent.children*.id.toSorted()
    }

    void "test cascading persist keeps the order of the children already cascaded"() {
        given:
            def parent = new CascadeGeneratedParent(name: "parent")
            def favourite = new CascadeAssignedChild(id: ID_SEQUENCE.incrementAndGet(), name: "B", parent: parent)
            parent.favourite = favourite
            parent.children = [
                    new CascadeAssignedChild(id: ID_SEQUENCE.incrementAndGet(), name: "A", parent: parent),
                    favourite,
                    new CascadeAssignedChild(id: ID_SEQUENCE.incrementAndGet(), name: "C", parent: parent)
            ]

        when: "a child of the collection is also cascaded by a to-one association"
            parent = generatedParentRepository.save(parent).block()

        then:
            parent.children*.name == ["A", "B", "C"]
            parent.children[1].is(favourite)
    }

    void "test cascading persist keeps the order of the children already persisted"() {
        given:
            def otherParent = new CascadeAssignedParent(id: ID_SEQUENCE.incrementAndGet(), name: "other")
            otherParent.children = [new CascadeGeneratedChild(name: "B", parent: otherParent)]
            def existing = assignedParentRepository.save(otherParent).block().children[0]
            def parent = new CascadeAssignedParent(id: ID_SEQUENCE.incrementAndGet(), name: "parent")
            parent.children = [
                    new CascadeGeneratedChild(name: "A", parent: parent),
                    existing,
                    new CascadeGeneratedChild(name: "C", parent: parent)
            ]

        when:
            parent = assignedParentRepository.save(parent).block()

        then:
            parent.children*.name == ["A", "B", "C"]
            parent.children[1].is(existing)
    }

    private static List<String> persistEvents(boolean batch, String... names) {
        if (batch) {
            return names.collect { "pre " + it } + names.collect { "post " + it }
        }
        return names.collectMany { ["pre " + it, "post " + it] }
    }
}

class CascadeEvents {
    static final List<String> EVENTS = []
}

interface CascadeUpdateParentRepository extends ReactorCrudRepository<CascadeUpdateParent, Long> {

    @Join(value = "children", type = Join.Type.FETCH)
    @Join(value = "favourite", type = Join.Type.FETCH)
    @Override
    Mono<CascadeUpdateParent> findById(Long id)
}

interface CascadeGeneratedParentRepository extends ReactorCrudRepository<CascadeGeneratedParent, Long> {

    @Join(value = "children", type = Join.Type.LEFT_FETCH)
    @Override
    Mono<CascadeGeneratedParent> findById(Long id)
}

interface CascadeAssignedParentRepository extends ReactorCrudRepository<CascadeAssignedParent, Long> {

    @Join(value = "children", type = Join.Type.LEFT_FETCH)
    @Override
    Mono<CascadeAssignedParent> findById(Long id)
}

@MappedEntity("cascade_upd_parent")
class CascadeUpdateParent {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
    @Nullable
    CascadeUpdateChild favourite
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent", cascade = Relation.Cascade.ALL)
    List<CascadeUpdateChild> children
}

@MappedEntity("cascade_upd_child")
class CascadeUpdateChild {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    CascadeUpdateParent parent
}

@MappedEntity("cascade_gen_parent")
class CascadeGeneratedParent {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
    @Nullable
    CascadeAssignedChild favourite
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent", cascade = Relation.Cascade.ALL)
    List<CascadeAssignedChild> children
}

@MappedEntity("cascade_assigned_child")
class CascadeAssignedChild {
    @Id
    Long id
    String name
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    @Nullable
    CascadeGeneratedParent parent

    @PrePersist
    void prePersist() {
        CascadeEvents.EVENTS.add("pre " + name)
    }

    @PostPersist
    void postPersist() {
        CascadeEvents.EVENTS.add("post " + name)
    }
}

@MappedEntity("cascade_assigned_parent")
class CascadeAssignedParent {
    @Id
    Long id
    String name
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent", cascade = Relation.Cascade.ALL)
    List<CascadeGeneratedChild> children
}

@MappedEntity("cascade_gen_child")
class CascadeGeneratedChild {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    CascadeAssignedParent parent

    @PrePersist
    void prePersist() {
        CascadeEvents.EVENTS.add("pre " + name)
    }

    @PostPersist
    void postPersist() {
        CascadeEvents.EVENTS.add("post " + name)
    }
}
