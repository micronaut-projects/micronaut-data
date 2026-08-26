package io.micronaut.data.nitrite

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.model.Pageable
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
import jakarta.persistence.criteria.JoinType
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

    void "an explicit criteria join populates a mappedBy to-many association"() {
        given:
        def parent = parentRepo.save(new OneToManyParent("Joined parent"))
        childRepo.saveAll([
                new OneToManyChild("Child A", parent),
                new OneToManyChild("Child B", parent)
        ])

        when:
        PredicateSpecification<OneToManyParent> byChildA = { root, cb ->
            def join = root.join("children")
            cb.equal(join.get("name"), "Child A")
        }
        def results = parentRepo.findAll(byChildA)
        def one = parentRepo.findOne(byChildA).orElseThrow()
        def page = parentRepo.findAll(byChildA, Pageable.from(0, 1))

        then:
        results.size() == 1
        results[0].children*.name.toSet() == ["Child A", "Child B"].toSet()
        one.children*.name.toSet() == ["Child A", "Child B"].toSet()
        page.content.size() == 1
        page.content[0].children*.name.toSet() == ["Child A", "Child B"].toSet()
    }

    void "a criteria join is fetched on the runtime-filter path of findOne"() {
        given:
        def parent = parentRepo.save(new OneToManyParent("Runtime filter parent"))
        childRepo.saveAll([
                new OneToManyChild("Child C", parent),
                new OneToManyChild("Child D", parent)
        ])

        when: "the predicate touches the root only, so the builder emits no lookup stage and a runtime filter is used"
        PredicateSpecification<OneToManyParent> byParentName = { root, cb ->
            root.join("children")
            cb.equal(root.get("name"), "Runtime filter parent")
        }
        def one = parentRepo.findOne(byParentName).orElseThrow()

        then: "the explicit join is still fetched, on the branch the string-query path never reaches"
        one.children*.name.toSet() == ["Child C", "Child D"].toSet()
    }

    void "an INNER criteria join excludes roots with no children"() {
        given:
        def withChildren = parentRepo.save(new OneToManyParent("Join type parent with children"))
        childRepo.save(new OneToManyChild("Child E", withChildren))
        parentRepo.save(new OneToManyParent("Join type parent without children"))

        when:
        PredicateSpecification<OneToManyParent> innerJoined = { root, cb ->
            root.join("children", JoinType.INNER)
            cb.like(root.get("name"), "Join type parent%")
        }
        def results = parentRepo.findAll(innerJoined)

        then: "an INNER join keeps only roots that have at least one child"
        results*.name == ["Join type parent with children"]
    }

    // The INNER restriction runs after Nitrite has already applied the page window, so an excluded
    // root leaves a short page rather than pulling the next row forward. Documented, not desired.
    void "an INNER criteria join restricts a page after the window is applied"() {
        given:
        def withChildren = parentRepo.save(new OneToManyParent("Paged parent with children"))
        childRepo.save(new OneToManyChild("Child F", withChildren))
        parentRepo.save(new OneToManyParent("Paged parent without children"))

        when:
        PredicateSpecification<OneToManyParent> innerJoined = { root, cb ->
            root.join("children", JoinType.INNER)
            cb.like(root.get("name"), "Paged parent%")
        }
        def page = parentRepo.findAll(innerJoined, Pageable.from(0, 2))

        then: "both rows fill the window, then the childless one is dropped from it"
        page.content*.name == ["Paged parent with children"]
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
                                             boolean retainDocuments,
                                             Object[] params,
                                             Map<String, Object> namedParameters) {
                    []
                }
            })
    }
}
