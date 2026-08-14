package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.nitrite.model.NitriteRefA
import io.micronaut.data.nitrite.model.NitriteRefB
import io.micronaut.data.nitrite.model.NitriteRefC
import io.micronaut.data.nitrite.repository.NitriteRefARepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteOneToOneSpec extends Specification implements NitriteTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteRefARepository refARepository = applicationContext.getBean(NitriteRefARepository)

    def cleanup() {
        refARepository.deleteAll()
    }

    void 'test one-to-one hierarchy'() {
        given:
            NitriteRefA newValue = new NitriteRefA(refB: new NitriteRefB(refC: new NitriteRefC(name: "TestXyz")))
        when:
            refARepository.save(newValue)
            NitriteRefA refA = refARepository.findById(newValue.id).get()
        then:
            refA.id
            refA.refB.id == newValue.refB.id
            refA.refB.refC.id == newValue.refB.refC.id
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

    void 'eager to-one hydration loads every nested association in a result set'() {
        given:
        refARepository.saveAll([
                new NitriteRefA(refB: new NitriteRefB(refC: new NitriteRefC(name: "First"))),
                new NitriteRefA(refB: new NitriteRefB(refC: new NitriteRefC(name: "Second")))
        ])

        when:
        def values = refARepository.findAll(Pageable.from(0, 10))

        then:
        values.size() == 2
        values*.refB*.refC*.name.sort() == ["First", "Second"]
    }
}
