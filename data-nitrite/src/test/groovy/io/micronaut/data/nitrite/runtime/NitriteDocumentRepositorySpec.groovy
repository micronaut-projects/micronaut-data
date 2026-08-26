package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.nitrite.model.NitriteDocument
import io.micronaut.data.nitrite.model.NitriteDocumentOwner
import io.micronaut.data.nitrite.model.NitriteMpPerson
import io.micronaut.data.nitrite.repository.NitriteCriteriaPersonRepository
import io.micronaut.data.nitrite.repository.NitriteDocumentEntityRepository
import io.micronaut.data.nitrite.repository.NitriteMpPersonRepository
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.data.nitrite.repository.NitriteDocumentEntityRepository.Specifications.tagsArrayContains

@MicronautTest
class NitriteDocumentRepositorySpec extends Specification implements NitriteTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteMpPersonRepository personRepository = applicationContext.getBean(NitriteMpPersonRepository)

    @Shared
    @Inject
    NitriteDocumentEntityRepository documentRepository = applicationContext.getBean(NitriteDocumentEntityRepository)

    @Shared
    @Inject
    NitriteCriteriaPersonRepository criteriaPersonRepository = applicationContext.getBean(NitriteCriteriaPersonRepository)

    def cleanup() {
        personRepository.deleteAll()
    }

    void "test custom find"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
            def peopleToUpdate = personRepository.findAll().toList()
            peopleToUpdate.forEach {it.age = 100 }
            personRepository.updateAll(peopleToUpdate)
        when:
            def allPeople = personRepository.findAll().toList()
        then:
            allPeople.size() == 4
            allPeople[0].age == 100
            allPeople[1].age == 100
            allPeople[2].age == 100
            allPeople[3].age == 100

        when:
            def people = personRepository.customFind("J.*").toList()

        then:
            people.size() == 2
            people[0].name == "James"
            people[0].age == 0
            people[1].name == "Jeff"
            people[1].age == 0
    }

    void "test custom find paginated"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis", "Josh", "Steven", "Jake", "Jim"])
            def peopleToUpdate = personRepository.findAll().toList()
            peopleToUpdate.forEach {it.age = 100 }
            personRepository.updateAll(peopleToUpdate)
        when:
            def peoplePage = personRepository.customFindPage("J.*", Pageable.from(0, 2))
            def people = peoplePage.getContent()
        then:
            peoplePage.hasTotalSize()
            peoplePage.getTotalPages() == 3
            peoplePage.pageNumber == 0
            people.size() == 2
            people[0].name == "Jake"
            people[0].age == 0
            people[1].name == "James"
        when:
            peoplePage = personRepository.customFindPage("J.*", peoplePage.nextPageable())
            people = peoplePage.getContent()
        then:
            peoplePage.hasTotalSize()
            peoplePage.getTotalPages() == 3
            peoplePage.pageNumber == 1
            people.size() == 2
            people[0].name == "Jeff"
            people[0].age == 0
            people[1].name == "Jim"
        when:
            peoplePage = personRepository.customFindPage("J.*", peoplePage.nextPageable())
            people = peoplePage.getContent()
        then:
            peoplePage.hasTotalSize()
            peoplePage.getTotalPages() == 3
            peoplePage.pageNumber == 2
            people.size() == 1
            people[0].name == "Josh"
    }

    void "test find by not like with parameterized pattern"() {
        given:
            savePersons(["John", "Joan", "Michael"])
        when:
            def matchingPeople = personRepository.findByNameLike("%Jo_n%")
            def notMatchingPeople = personRepository.findByNameNotLike("%Jo_n%")
        then:
            matchingPeople*.name.toSet() == ["John", "Joan"] as Set
            notMatchingPeople*.name == ["Michael"]
    }

    void "test find by array contains"() {
        given:
            def doc1 = new NitriteDocument(title: "Doc1", tags: ["red", "blue", "white"])
            documentRepository.save(doc1)
            def doc2 = new NitriteDocument(title: "Doc2", tags: ["red", "blue"])
            documentRepository.save(doc2)
            def doc3 = new NitriteDocument(title: "Doc3")
            documentRepository.save(doc3)
        when:
            def result1 = documentRepository.findByTagsArrayContains("red")
            def result2 = documentRepository.findByTagsArrayContains("grey")
            def result3 = documentRepository.findByTagsArrayContains(["red", "blue"])
            def result4 = documentRepository.findByTagsArrayContains(["red", "blue", "white"])
            def result5 = documentRepository.findByTagsArrayContains(["red", "white"])
        then:
            result1.size() == 2
            result2.size() == 0
            result3.size() == 2
            result4.size() == 1
            result5.size() == 1
        when:"Test with criteria spec"
            result1 = documentRepository.findAll(tagsArrayContains("red"))
            result2 = documentRepository.findAll(tagsArrayContains("grey"))
            result3 = documentRepository.findAll(tagsArrayContains("red", "blue"))
            result4 = documentRepository.findAll(tagsArrayContains("red", "blue", "white"))
            result5 = documentRepository.findAll(tagsArrayContains("red", "white"))
        then:
            result1.size() == 2
            result2.size() == 0
            result3.size() == 2
            result4.size() == 1
            result5.size() == 1
        cleanup:
            documentRepository.deleteAll()
    }

    void "test entity with map of objects"() {
        given:
            def doc1 = new NitriteDocument(title: "Doc1", tags: ["red", "blue", "white"])
            def owner1 = new NitriteDocumentOwner("Owner1")
            owner1.age = 40
            def owner2 = new NitriteDocumentOwner("Owner2")
            owner2.age = 30
            doc1.owners = ["owner1": owner1, "owner2": owner2]
            documentRepository.save(doc1)
        when:
            def optDoc = documentRepository.findById(doc1.id)
        then:
            optDoc.present
            def doc = optDoc.get()
            doc.owners.size() == 2
            def docOwner1 = doc.owners["owner1"]
            docOwner1.name == "Owner1"
            docOwner1.age == 40
            docOwner1.class == NitriteDocumentOwner
            def docOwner2 = doc.owners["owner2"]
            docOwner2.name == "Owner2"
            docOwner2.age == 30
        cleanup:
            documentRepository.deleteAll()
    }

    void "test criteria find IN"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
            def people = criteriaPersonRepository.findAll().toList()
        then:
            people.size() == 4
        when:
            def limitedPeople1 = criteriaPersonRepository.findAll(new QuerySpecification<NitriteMpPerson>() {
                @Override
                Predicate toPredicate(Root<NitriteMpPerson> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                    return root.get("id").in(people.get(0).getId(), people.get(1).getId(), people.get(2).getId())
                }
            })
        then:
            limitedPeople1.size() == 3
            people.collect { it.id }.containsAll(limitedPeople1.collect { it.id })
        when:
            def limitedPeople2 = criteriaPersonRepository.findAll(new QuerySpecification<NitriteMpPerson>() {
                @Override
                Predicate toPredicate(Root<NitriteMpPerson> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                    return criteriaBuilder.in(root.get("id"))
                            .value(people.get(0).getId())
                            .value(people.get(1).getId())
                            .value(people.get(2).getId())
                }
            })
        then:
            limitedPeople2.size() == 3
            people.collect { it.id }.containsAll(limitedPeople2.collect { it.id })
    }

    private List<NitriteMpPerson> savePersons(List<String> names) {
        names.collect { name -> personRepository.save(new NitriteMpPerson(name)) }
    }
}
