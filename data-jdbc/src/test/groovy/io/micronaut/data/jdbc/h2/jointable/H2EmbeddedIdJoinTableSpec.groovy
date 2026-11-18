package io.micronaut.data.jdbc.h2.jointable

import groovy.transform.EqualsAndHashCode
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.sql.JoinTable
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.sql.Connection
import java.sql.ResultSet

@MicronautTest(transactional = false)
class H2EmbeddedIdJoinTableSpec extends Specification implements H2TestPropertyProvider {

    @Inject
    JtShelfRepository shelfRepository

    @Inject
    JtLibraryRepository libraryRepository

    @Inject
    ConnectionOperations<Connection> connectionOperations

    void "join table rows of an owner with an embedded id hold both id columns"() {
        given:
            def shelf = new JtShelf(
                    id: new JtShelfKey(room: "north", position: 7),
                    name: "Classics",
                    books: [new JtBook(title: "Dune"), new JtBook(title: "Emma")]
            )

        when:
            shelfRepository.save(shelf)

        then:
            joinTableRows("SELECT jt_shelf_id_room, jt_shelf_id_position, jt_book_id FROM jt_shelf_book ORDER BY jt_book_id") == [
                    ["north", 7, shelf.books[0].id],
                    ["north", 7, shelf.books[1].id]
            ]

        when:
            def found = shelfRepository.findById(new JtShelfKey(room: "north", position: 7)).get()

        then:
            found.name == "Classics"
            found.books*.title.sort() == ["Dune", "Emma"]
    }

    void "join table rows of a target with an embedded id hold both id columns"() {
        given: "shelves that already exist, since a join table only links children that have an id"
            def shelves = shelfRepository.saveAll([
                    new JtShelf(id: new JtShelfKey(room: "east", position: 1), name: "Poetry", books: []),
                    new JtShelf(id: new JtShelfKey(room: "east", position: 2), name: "Drama", books: [])
            ])
            def library = new JtLibrary(name: "City", shelves: shelves)

        when:
            libraryRepository.save(library)

        then:
            joinTableRows("SELECT jt_library_id, jt_shelf_id_room, jt_shelf_id_position FROM jt_library_shelf ORDER BY jt_shelf_id_position") == [
                    [library.id, "east", 1],
                    [library.id, "east", 2]
            ]

        when:
            def found = libraryRepository.findById(library.id).get()

        then:
            found.shelves*.name.sort() == ["Drama", "Poetry"]
    }

    private List<List<Object>> joinTableRows(String sql) {
        return connectionOperations.executeRead { status ->
            status.connection.prepareStatement(sql).withCloseable { statement ->
                statement.executeQuery().withCloseable { rs -> readRows(rs) }
            }
        }
    }

    private static List<List<Object>> readRows(ResultSet rs) {
        List<List<Object>> rows = []
        int count = rs.metaData.columnCount
        while (rs.next()) {
            rows << (1..count).collect { rs.getObject(it) }
        }
        return rows
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface JtShelfRepository extends CrudRepository<JtShelf, JtShelfKey> {

    @Join(value = "books", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<JtShelf> findById(JtShelfKey id)
}

@JdbcRepository(dialect = Dialect.H2)
interface JtLibraryRepository extends CrudRepository<JtLibrary, Long> {

    @Join(value = "shelves", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<JtLibrary> findById(Long id)
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
