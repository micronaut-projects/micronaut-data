package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.NitriteOtoChild
import io.micronaut.data.nitrite.model.NitriteOtoParent
import io.micronaut.data.nitrite.mongoport.NitriteTestPropertyProvider
import io.micronaut.data.nitrite.repository.NitriteOtoParentRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteOneToManySpec extends Specification implements NitriteTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteOtoParentRepository parentRepository = applicationContext.getBean(NitriteOtoParentRepository)

    def cleanup() {
        parentRepository.deleteAll()
    }

    void 'test one-to-many relationship'() {
        given:
            NitriteOtoParent parent = new NitriteOtoParent(name: "Parent")
            NitriteOtoChild child1 = new NitriteOtoChild(name: "Child1")
            NitriteOtoChild child2 = new NitriteOtoChild(name: "Child2")
            parent.children = [child1, child2]
            child1.parent = parent
            child2.parent = parent

        when:
            parentRepository.save(parent)
            NitriteOtoParent saved = parentRepository.findById(parent.id).get()

        then:
            saved.id
            saved.name == "Parent"
            saved.children.size() == 2
            saved.children[0].name == "Child1"
            saved.children[1].name == "Child2"

        when:
            saved.name = "Parent Updated"
            parentRepository.update(saved)
            NitriteOtoParent updated = parentRepository.findById(saved.id).get()

        then:
            updated.name == "Parent Updated"
            updated.children.size() == 2
    }

    void 'test one-to-many with empty collection'() {
        given:
            NitriteOtoParent parent = new NitriteOtoParent(name: "Empty Parent")

        when:
            parentRepository.save(parent)
            NitriteOtoParent saved = parentRepository.findById(parent.id).get()

        then:
            saved.children != null
            saved.children.isEmpty()
    }
}
