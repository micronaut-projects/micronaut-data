package io.micronaut.data.document.mongodb

import groovy.transform.ToString
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class MongoManyToOneSpec extends Specification implements MongoTestPropertyProvider {
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

@MongoRepository
interface RefARepository extends CrudRepository<RefA, String> {

    @Join(value = "refB")
    @Join(value = "refB.refC")
    Page<RefA> findAll(Pageable pageable)

    @Join(value = "refB")
    @Join(value = "refB.refC")
    List<RefA> queryAll(Pageable pageable)

    @Join(value = "refB")
    @Join(value = "refB.refC")
    @Override
    Optional<RefA> findById(String id)
}

@ToString
@MappedEntity("ref_a")
class RefA {
    @Id
    @GeneratedValue
    String id
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    RefB refB
}

@ToString
@MappedEntity("ref_b")
class RefB {
    @Id
    @GeneratedValue
    String id
    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    RefC refC
}

@ToString
@MappedEntity("ref_c")
class RefC {
    @Id
    @GeneratedValue
    String id
    String name
}