package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.OneToManyChild
import io.micronaut.data.nitrite.model.OneToManyParent
import io.micronaut.data.nitrite.repository.OneToManyChildRepository
import io.micronaut.data.nitrite.repository.OneToManyParentRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class NitriteOneToManyFilterSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run([
        "micronaut.nitrite.default.storage-mode": "IN_MEMORY"
    ])

    @Shared
    OneToManyParentRepository parentRepo = context.getBean(OneToManyParentRepository)
    @Shared
    OneToManyChildRepository childRepo = context.getBean(OneToManyChildRepository)

    def setup() {
        childRepo.deleteAll()
        parentRepo.deleteAll()
    }

    void "test ONE_TO_MANY filtering (Parent.children.name)"() {
        given:
        def parent1 = new OneToManyParent("Parent 1")
        def parent2 = new OneToManyParent("Parent 2")
        parentRepo.saveAll([parent1, parent2])

        def child1 = new OneToManyChild("Child A", parent1)
        def child2 = new OneToManyChild("Child B", parent1)
        def child3 = new OneToManyChild("Child A", parent2) // Same name, different parent
        childRepo.saveAll([child1, child2, child3])

        when:
        def parentsWithChildA = parentRepo.findByChildrenName("Child A")

        then:
        parentsWithChildA.size() == 2
        parentsWithChildA.collect { it.name }.sort() == ["Parent 1", "Parent 2"]

        when:
        def parentsWithChildB = parentRepo.findByChildrenName("Child B")

        then:
        parentsWithChildB.size() == 1
        parentsWithChildB[0].name == "Parent 1"
    }

    void "test MANY_TO_ONE filtering (Child.parent.name)"() {
        given:
        def parent1 = new OneToManyParent("Parent 1")
        def parent2 = new OneToManyParent("Parent 2")
        parentRepo.saveAll([parent1, parent2])

        def child1 = new OneToManyChild("Child A", parent1)
        def child2 = new OneToManyChild("Child B", parent2)
        childRepo.saveAll([child1, child2])

        when:
        def childrenOfParent1 = childRepo.findByParentName("Parent 1")

        then:
        childrenOfParent1.size() == 1
        childrenOfParent1[0].name == "Child A"
    }
}
