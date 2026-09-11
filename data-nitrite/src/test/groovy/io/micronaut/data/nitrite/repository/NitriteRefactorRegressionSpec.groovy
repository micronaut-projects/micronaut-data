package io.micronaut.data.nitrite.repository

import io.micronaut.data.nitrite.model.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import io.micronaut.data.nitrite.model.CriteriaPerson
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification

@MicronautTest(transactional = false)
class NitriteRefactorRegressionSpec extends Specification {

    @Inject
    RegressionRepository repository

    @Inject
    CriteriaPersonRepository criteriaRepository

    def setup() {
        repository.deleteAll()
    }

    void "@Query single field with two operators must AND them (range)"() {
        given:
        repository.save(new Person("A", 10))
        repository.save(new Person("B", 20))
        repository.save(new Person("C", 30))

        when:
        def results = repository.findByAgeRangeJson(15, 25)

        then:
        results.size() == 1
        results[0].name == "B"
        results[0].age == 20
    }

    void "Criteria update must not persist ParameterExpressionImpl"() {
        given:
        criteriaRepository.save(new CriteriaPerson("Denis", 13))

        when:
        UpdateSpecification<CriteriaPerson> setName = (root, query, cb) -> {
            query.set(root.get("name"), "Steven")
            return null
        }
        PredicateSpecification<CriteriaPerson> denis = (root, cb) -> cb.equal(root.get("name"), "Denis")
        def updated = criteriaRepository.updateAll(setName.where(denis))

        then:
        updated == 1
        def person = criteriaRepository.findAll().find { it.age == 13 }
        person.name == "Steven"
        // If it failed, person.name might be "ParameterExpressionImpl{...}"
    }
}
