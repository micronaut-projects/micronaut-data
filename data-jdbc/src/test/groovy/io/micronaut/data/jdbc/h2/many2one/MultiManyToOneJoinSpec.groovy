package io.micronaut.data.jdbc.h2.many2one


import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.*
import io.micronaut.data.annotation.sql.JoinColumn
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
@H2DBProperties
class MultiManyToOneJoinSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    RefARepository refARepository = applicationContext.getBean(RefARepository)

    @Shared
    @Inject
    CustomBookRepository customBookRepository = applicationContext.getBean(CustomBookRepository)

    void 'test many-to-one hierarchy'() {
        given:
            RefA refA = new RefA(refB: new RefB(refC: new RefC(name: "TestXyz")))
        when:
            refARepository.save(refA)
            refA = refARepository.findById(refA.id).get()
        then:
            refA.id
            refA.refB.refC.name == "TestXyz"
        when:
            def list = refARepository.queryAll(Pageable.from(0, 10))
        then:
            list.size() == 1
            list[0].refB.refC.name == "TestXyz"
        when:
            def page = refARepository.findAll(Pageable.from(0, 10))
        then:
            page.content.size() == 1
            page.content[0].refB.refC.name == "TestXyz"
        when:
            refARepository.update(refA)
            refA = refARepository.findById(refA.id).get()
        then:
            refA.id
            refA.refB.refC.name == "TestXyz"
    }

    void "test join via non identity join column"() {
        given:
        def customAuthor = new CustomAuthor()
        customAuthor.name = "author1"
        customAuthor.id2 = 20
        def customBook = new CustomBook()
        customBook.title = "book1"
        customBook.pages = 100
        customBook.author = customAuthor
        customBookRepository.save(customBook)
        when:
        def books = customBookRepository.findAll()
        then:
        books.size() == 1
        books[0].author.id2 == 20
        cleanup:
        customBookRepository.deleteAll()
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface RefARepository extends CrudRepository<RefA, Long> {

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    Page<RefA> findAll(Pageable pageable)

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    List<RefA> queryAll(Pageable pageable)

    @Join(value = "refB", type = Join.Type.LEFT_FETCH)
    @Join(value = "refB.refC", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<RefA> findById(Long aLong)
}

@MappedEntity
class RefA {
    @Id
    @GeneratedValue
    Long id
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    RefB refB
}

@MappedEntity
class RefB {
    @Id
    @GeneratedValue
    Long id
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    RefC refC
}

@MappedEntity
class RefC {
    @Id
    @GeneratedValue
    Long id
    String name
}

@JdbcRepository(dialect = Dialect.H2)
@Join("author")
interface CustomBookRepository extends CrudRepository<CustomBook, Long> {
}

@MappedEntity(value = "custauthor1")
class CustomAuthor {
    @GeneratedValue
    @Id
    private Long id
    private Long id2
    private String name

    Long getId() { return id }
    void setId(Long id) { this.id = id }
    Long getId2() { return id2 }
    void setId2(Long id2) { this.id2 = id2 }
    String getName() { return name }
    void setName(String name) { this.name = name }
}

@MappedEntity(value = "custbook1")
class CustomBook {
    @GeneratedValue
    @Id
    private Long id
    private String title
    private int pages
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    @JoinColumn(name = "author_id2", referencedColumnName = "id2")
    private CustomAuthor author

    Long getId() { return id }
    void setId(Long id) { this.id = id }
    String getTitle() { return title }
    void setTitle(String title) { this.title = title }
    int getPages() { return pages }
    void setPages(int pages) { this.pages = pages }
    CustomAuthor getAuthor() { return author }
    void setAuthor(CustomAuthor author) { this.author = author }
}
