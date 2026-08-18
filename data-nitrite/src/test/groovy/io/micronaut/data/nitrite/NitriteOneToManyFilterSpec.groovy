package io.micronaut.data.nitrite

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.nitrite.model.OneToManyChild
import io.micronaut.data.nitrite.model.OneToManyParent
import io.micronaut.data.nitrite.repository.NitriteComplexEntityRepository
import io.micronaut.data.nitrite.repository.OneToManyChildRepository
import io.micronaut.data.nitrite.repository.OneToManyParentRepository
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteOneToManyFilterSpec extends Specification {

    @Inject
    ConversionService conversionService

    @Inject
    RuntimeEntityRegistry runtimeEntityRegistry

    @Inject
    OneToManyParentRepository parentRepo

    @Inject
    OneToManyChildRepository childRepo

    @Inject
    NitriteComplexEntityRepository complexRepo

    def setup() {
        childRepo.deleteAll()
        parentRepo.deleteAll()
        complexRepo.deleteAll()
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

        when:
        def parentsWithChildC = parentRepo.findByChildrenName("Child C")

        then: "a name no child carries matches nothing, rather than degrading to match-all"
        parentsWithChildC.isEmpty()
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

    void "an association whose target has no identity cannot be reverse-looked-up"() {
        when: "the target of NitriteComplexEntity.values carries no @Id, so no sub-query can select ids"
        def results = complexRepo.findByValuesKey("some key with space")

        then: "the read matches nothing rather than every document"
        results.isEmpty()

        when: "the same association is filtered through the Criteria API"
        def directResults = complexRepo.findAll({ root, cb ->
            cb.equal(root.get("values"), "test key with space")
        } as PredicateSpecification)

        then:
        directResults.isEmpty()
    }

    void "a reverse lookup on the association itself filters the target on its mappedBy inverse"() {
        given:
        def filterBuilder = reverseLookupFilterBuilder()
        def parentEntity = runtimeEntityRegistry.getEntity(OneToManyParent)

        when: "no target property is named, so the child's FK back to the parent is the filter"
        def filter = filterBuilder
            .buildFieldFilter(parentEntity, "children", ["\$eq": "some id with space"], new Object[0], [:])

        then: "an equality on that FK, not a match-all"
        filter.toString() == MAPPED_BY_FILTER
    }

    void "a reverse lookup naming a target property resolves it through a sub-query: #shape"() {
        given:
        def filterBuilder = reverseLookupFilterBuilder()
        def parentEntity = runtimeEntityRegistry.getEntity(OneToManyParent)

        when: "the value carries a space, so it is treated as a target-property value rather than an id"
        def filter = filterBuilder
            .buildFieldFilter(parentEntity, path, ["\$eq": "some id with space"], new Object[0], [:])

        then: "the sub-query found no ids, so the result is an id filter rather than the mappedBy one"
        filter != null
        filter.toString() != MAPPED_BY_FILTER

        where:
        shape                                       | path
        "the mappedBy inverse"                      | "children.parent"
        "a property the target does not declare"    | "children.invalidProp"
        "a plain property of the target"            | "children.name"
        "an association of the wrong relation kind" | "children.siblings"
        "the target identity"                       | "children.id"
    }

    /** The filter produced when the reverse lookup goes straight to the child's parent FK. */
    private static final String MAPPED_BY_FILTER = "(one_to_many_child_one_to_many_parent == some id with space)"

    /**
     * A builder whose sub-query executor finds no ids, so only the filter shape is under test.
     */
    private NitriteFilterBuilder reverseLookupFilterBuilder() {
        new NitriteFilterBuilder(
            new NitriteEntityMapper(conversionService, null, runtimeEntityRegistry),
            new NitriteFilterBuilder.SubQueryExecutor() {
                @Override
                List<Object> executeSubQuery(RuntimePersistentEntity<?> associatedEntity,
                                             Map<String, Object> filterMap,
                                             String targetField,
                                             Object[] params,
                                             Map<String, Object> namedParameters) {
                    []
                }
            })
    }
}
