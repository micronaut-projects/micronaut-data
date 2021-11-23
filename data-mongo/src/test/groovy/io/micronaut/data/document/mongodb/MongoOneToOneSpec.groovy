package io.micronaut.data.document.mongodb

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
class MultiOneToOneJoinSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    CRefARepository refARepository = applicationContext.getBean(CRefARepository)

    void 'test one-to-one hierarchy'() {
        given:
            CRefA newValue = new CRefA(refB: new CRefB(refC: new CRefC(name: "TestXyz")))
        when:
            refARepository.save(newValue)
            CRefA refA = refARepository.findById(newValue.id).get()
        then:
            refA.id
            refA.refB.id == newValue.refB.id
            refA.refB.refC.id == newValue.refB.refC.id
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
interface CRefARepository extends CrudRepository<CRefA, String> {

    @Join(value = "refB")
    @Join(value = "refB.refC")
    Page<CRefA> findAll(Pageable pageable)

    @Join(value = "refB")
    @Join(value = "refB.refC")
    List<CRefA> queryAll(Pageable pageable)

    @Join(value = "refB.refC")
    @Override
    Optional<CRefA> findById(String id)
}

@MappedEntity("one_a")
class CRefA {
    @Id
    @GeneratedValue
    String id
    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
    CRefB refB
}

@MappedEntity("one_b")
class CRefB {
    @Id
    @GeneratedValue
    String id
    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
    CRefC refC
}

@MappedEntity("one_c")
class CRefC {
    @Id
    @GeneratedValue
    String id
    String name
}