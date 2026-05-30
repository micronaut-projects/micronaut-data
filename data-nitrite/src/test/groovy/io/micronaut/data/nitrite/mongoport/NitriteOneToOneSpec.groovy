package io.micronaut.data.nitrite.mongoport

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Join;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteRefA;
import io.micronaut.data.nitrite.mongoport.entities.NitriteRefB;
import io.micronaut.data.nitrite.mongoport.entities.NitriteRefC;
import io.micronaut.data.nitrite.mongoport.repositories.NitriteRefARepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.spock.annotation.MicronautTest;
import jakarta.inject.Inject;
import spock.lang.AutoCleanup;
import spock.lang.Shared;
import spock.lang.Specification;

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
}
