package io.micronaut.data.r2dbc.h2.jointable

import groovy.transform.EqualsAndHashCode
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.sql.JoinTable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.h2.H2TestPropertyProvider
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

@MicronautTest(transactional = false)
class H2EmbeddedIdJoinTableSpec extends Specification implements H2TestPropertyProvider {

    @Inject
    JtShelfRepository shelfRepository

    @Inject
    JtLibraryRepository libraryRepository

    @Inject
    ConnectionFactory connectionFactory

    void "join table rows of an owner with an embedded id hold both id columns"() {
        given:
            def shelf = new JtShelf(
                    id: new JtShelfKey(room: "north", position: 7),
                    name: "Classics",
                    books: [new JtBook(title: "Dune"), new JtBook(title: "Emma")]
            )

        when:
            shelfRepository.save(shelf).block()

        then:
            joinTableRows("SELECT jt_shelf_id_room, jt_shelf_id_position, jt_book_id FROM jt_shelf_book ORDER BY jt_book_id", 3) == [
                    ["north", 7, shelf.books[0].id],
                    ["north", 7, shelf.books[1].id]
            ]

        when:
            def found = shelfRepository.findById(new JtShelfKey(room: "north", position: 7)).block()

        then:
            found.name == "Classics"
            found.books*.title.sort() == ["Dune", "Emma"]
    }

    void "join table rows of a target with an embedded id hold both id columns"() {
        given: "shelves that already exist, since a join table only links children that have an id"
            def shelves = shelfRepository.saveAll([
                    new JtShelf(id: new JtShelfKey(room: "east", position: 1), name: "Poetry", books: []),
                    new JtShelf(id: new JtShelfKey(room: "east", position: 2), name: "Drama", books: [])
            ]).collectList().block()
            def library = new JtLibrary(name: "City", shelves: shelves)

        when:
            libraryRepository.save(library).block()

        then:
            joinTableRows("SELECT jt_library_id, jt_shelf_id_room, jt_shelf_id_position FROM jt_library_shelf ORDER BY jt_shelf_id_position", 3) == [
                    [library.id, "east", 1],
                    [library.id, "east", 2]
            ]

        when:
            def found = libraryRepository.findById(library.id).block()

        then:
            found.shelves*.name.sort() == ["Drama", "Poetry"]
    }

    private List<List<Object>> joinTableRows(String sql, int columnCount) {
        return Flux.usingWhen(
                Mono.from(connectionFactory.create()),
                (Connection connection) -> Flux.from(connection.createStatement(sql).execute())
                        .flatMap(result -> result.map((row, metadata) -> rowValues(row, columnCount))),
                (Connection connection) -> connection.close()
        ).collectList().block()
    }

    private static List<Object> rowValues(Row row, int columnCount) {
        return (0..<columnCount).collect { row.get(it) }
    }
}

@R2dbcRepository(dialect = Dialect.H2)
interface JtShelfRepository extends ReactorCrudRepository<JtShelf, JtShelfKey> {

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    @Override
    Mono<JtShelf> findById(JtShelfKey id)
}

@R2dbcRepository(dialect = Dialect.H2)
interface JtLibraryRepository extends ReactorCrudRepository<JtLibrary, Long> {

    @Join(value = "shelves", type = Join.Type.LEFT_FETCH)
    @Override
    Mono<JtLibrary> findById(Long id)
}

@EqualsAndHashCode
@Embeddable
class JtShelfKey {
    String room
    Integer position
}

@MappedEntity("jt_shelf")
class JtShelf {
    @EmbeddedId
    JtShelfKey id
    String name
    @JoinTable(name = "jt_shelf_book")
    @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.PERSIST)
    List<JtBook> books
}

@MappedEntity("jt_book")
class JtBook {
    @Id
    @GeneratedValue
    Long id
    String title
}

@MappedEntity("jt_library")
class JtLibrary {
    @Id
    @GeneratedValue
    Long id
    String name
    @JoinTable(name = "jt_library_shelf")
    @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.PERSIST)
    List<JtShelf> shelves
}
