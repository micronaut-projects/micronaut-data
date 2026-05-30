package io.micronaut.data.nitrite.mongoport

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.nitrite.mongoport.entities.NitriteMtoRefA
import io.micronaut.data.nitrite.mongoport.entities.NitriteMtoRefB
import io.micronaut.data.nitrite.mongoport.entities.NitriteMtoRefC
import io.micronaut.data.nitrite.mongoport.repositories.NitriteMtoRefARepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteManyToOneSpec extends Specification implements NitriteTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteMtoRefARepository refARepository = applicationContext.getBean(NitriteMtoRefARepository)

    def cleanup() {
        refARepository.deleteAll()
    }

    void 'test many-to-one hierarchy'() {
        given:
            NitriteMtoRefA refA = new NitriteMtoRefA(refB: new NitriteMtoRefB(refC: new NitriteMtoRefC(name: "TestXyz")))
        when:
            refARepository.save(refA)
            refA = refARepository.findById(refA.id).get()
        then:
            refA.id
            refA.refB.refC.name == "TestXyz"
        when:
            def list = refARepository.findAll(Pageable.from(0, 10))
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
