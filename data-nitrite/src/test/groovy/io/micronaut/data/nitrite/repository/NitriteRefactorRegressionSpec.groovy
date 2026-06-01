package io.micronaut.data.nitrite.repository

import io.micronaut.data.nitrite.model.CriteriaPerson
import io.micronaut.data.nitrite.model.Person
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteRefactorRegressionSpec extends Specification {

    @Inject
    RegressionRepository repository

    @Inject
    CriteriaPersonRepository criteriaRepository

    def setup() {
        repository.deleteAll()
        criteriaRepository.deleteAll()
    }

    void "JSON update must ignore \$set in filter building"() {
        given:
        repository.save(new Person("John", 30))

        when:
        // This query has $set in the root, which should be used for update but ignored for filter
        int updated = repository.updateAgeJson("John", 40)

        then:
        updated == 1
        repository.findByName("John").get().age == 40
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
