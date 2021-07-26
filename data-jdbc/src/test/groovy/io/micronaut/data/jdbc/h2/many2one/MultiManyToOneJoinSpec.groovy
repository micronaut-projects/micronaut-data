package io.micronaut.data.jdbc.h2.many2one


import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.*
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