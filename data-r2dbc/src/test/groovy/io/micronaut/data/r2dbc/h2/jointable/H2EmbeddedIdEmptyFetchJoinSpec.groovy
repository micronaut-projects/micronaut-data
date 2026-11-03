package io.micronaut.data.r2dbc.h2.jointable

import groovy.transform.EqualsAndHashCode
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.h2.H2TestPropertyProvider
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.jspecify.annotations.Nullable
import reactor.core.publisher.Mono
import spock.lang.Specification

@MicronautTest(transactional = false)
class H2EmbeddedIdEmptyFetchJoinSpec extends Specification implements H2TestPropertyProvider {

    @Inject
    JtLibraryRepository libraryRepository

    @Inject
    JtRackRepository rackRepository

    @Inject
    JtSlotRepository slotRepository

    @Inject
    JtShelfRepository shelfRepository

    @Inject
    JtLabelRepository labelRepository

    void "a join table fetch without rows reads no embedded-id targets"() {
        given:
            def library = libraryRepository.save(new JtLibrary(name: "Empty", shelves: [])).block()

        when:
            def found = libraryRepository.findById(library.id).block()

        then:
            found.shelves.isEmpty()
    }

    void "a mapped-by fetch without rows reads no embedded-id children"() {
        given:
            def rack = rackRepository.save(new JtRack(name: "Empty")).block()

        when:
            def found = rackRepository.findById(rack.id).block()

        then:
            found.slots.isEmpty()
    }

    void "a mapped-by fetch reads every embedded-id child"() {
        given:
            def rack = rackRepository.save(new JtRack(name: "Full")).block()
            slotRepository.save(new JtSlot(id: new JtSlotKey(row: 1, col: 1), rack: rack, name: "a")).block()
            slotRepository.save(new JtSlot(id: new JtSlotKey(row: 1, col: 2), rack: rack, name: "b")).block()

        when:
            def found = rackRepository.findById(rack.id).block()

        then:
            found.slots*.name.sort() == ["a", "b"]
    }

    void "a null many-to-one to an embedded-id target reads as null"() {
        given:
            def label = labelRepository.save(new JtLabel(text: "loose")).block()

        when:
            def found = labelRepository.findById(label.id).block()
            def fetched = labelRepository.queryById(label.id).block()

        then:
            found.shelf == null
            fetched.shelf == null
    }

    void "a many-to-one to an embedded-id target reads the target"() {
        given:
            def shelf = shelfRepository.save(new JtShelf(id: new JtShelfKey(room: "west", position: 9), name: "Maps", books: [])).block()
            def label = labelRepository.save(new JtLabel(text: "pinned", shelf: shelf)).block()

        when:
            def found = labelRepository.findById(label.id).block()
            def fetched = labelRepository.queryById(label.id).block()

        then:
            found.shelf.id == new JtShelfKey(room: "west", position: 9)
            fetched.shelf.id == new JtShelfKey(room: "west", position: 9)
            fetched.shelf.name == "Maps"
    }
}

@R2dbcRepository(dialect = Dialect.H2)
interface JtLabelRepository extends ReactorCrudRepository<JtLabel, Long> {

    @Join(value = "shelf", type = Join.Type.LEFT_FETCH)
    Mono<JtLabel> queryById(Long id)
}

@MappedEntity("jt_label")
class JtLabel {
    @Id
    @GeneratedValue
    Long id
    String text
    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    JtShelf shelf
}

@R2dbcRepository(dialect = Dialect.H2)
interface JtRackRepository extends ReactorCrudRepository<JtRack, Long> {

    @Join(value = "slots", type = Join.Type.LEFT_FETCH)
    @Override
    Mono<JtRack> findById(Long id)
}

@R2dbcRepository(dialect = Dialect.H2)
interface JtSlotRepository extends ReactorCrudRepository<JtSlot, JtSlotKey> {
}

@MappedEntity("jt_rack")
class JtRack {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "rack")
    List<JtSlot> slots
}

@EqualsAndHashCode
@Embeddable
class JtSlotKey {
    Integer row
    Integer col
}

@MappedEntity("jt_slot")
class JtSlot {
    @EmbeddedId
    JtSlotKey id
    @Relation(Relation.Kind.MANY_TO_ONE)
    JtRack rack
    String name
}
