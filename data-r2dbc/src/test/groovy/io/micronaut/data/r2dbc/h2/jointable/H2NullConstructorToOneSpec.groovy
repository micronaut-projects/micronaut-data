package io.micronaut.data.r2dbc.h2.jointable

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
class H2NullConstructorToOneSpec extends Specification implements H2TestPropertyProvider {

    @Inject
    JtTagRepository tagRepository

    @Inject
    JtShelfRepository shelfRepository

    @Inject
    JtBookRepository bookRepository

    void "null many-to-one constructor arguments read as null"() {
        given:
            def tag = tagRepository.save(new JtTag("loose", null, null)).block()

        when:
            def found = tagRepository.findById(tag.id).block()
            def fetched = tagRepository.queryById(tag.id).block()

        then:
            found.book == null
            found.shelf == null
            fetched.book == null
            fetched.shelf == null
    }

    void "many-to-one constructor arguments read their targets"() {
        given:
            def book = bookRepository.save(new JtBook(title: "Atlas")).block()
            def shelf = shelfRepository.save(new JtShelf(id: new JtShelfKey(room: "south", position: 3), name: "Travel", books: [])).block()
            def tag = tagRepository.save(new JtTag("pinned", book, shelf)).block()

        when:
            def found = tagRepository.findById(tag.id).block()
            def fetched = tagRepository.queryById(tag.id).block()

        then:
            found.book.id == book.id
            found.shelf.id == new JtShelfKey(room: "south", position: 3)
            fetched.book.title == "Atlas"
            fetched.shelf.name == "Travel"
    }
}

@R2dbcRepository(dialect = Dialect.H2)
interface JtTagRepository extends ReactorCrudRepository<JtTag, Long> {

    @Join(value = "book", type = Join.Type.LEFT_FETCH)
    @Join(value = "shelf", type = Join.Type.LEFT_FETCH)
    Mono<JtTag> queryById(Long id)
}

@R2dbcRepository(dialect = Dialect.H2)
interface JtBookRepository extends ReactorCrudRepository<JtBook, Long> {
}

@MappedEntity("jt_tag")
class JtTag {
    @Id
    @GeneratedValue
    Long id
    final String text
    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    final JtBook book
    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    final JtShelf shelf

    JtTag(String text, @Nullable JtBook book, @Nullable JtShelf shelf) {
        this.text = text
        this.book = book
        this.shelf = shelf
    }
}
