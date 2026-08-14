package io.micronaut.data.nitrite.repository

import io.micronaut.data.nitrite.model.CriteriaPerson
import io.micronaut.data.nitrite.model.Person
import io.micronaut.data.nitrite.model.SnakeEntity
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

    @Inject
    SnakeEntityRepository snakeEntityRepository

    def setup() {
        repository.deleteAll()
        criteriaRepository.deleteAll()
        snakeEntityRepository.deleteAll()
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

    void "derived update with @Id must update only the matching entity"() {
        given:
        def saved = repository.save(new Person("Alice", 30))

        when:
        repository.update(saved.id, "Alicia")

        then:
        repository.findByName("Alicia").present
        !repository.findByName("Alice").present
    }

    void "@Query with top-level \$and must filter by both conditions"() {
        given:
        repository.save(new Person("Alice", 30))
        repository.save(new Person("Alice", 20))
        repository.save(new Person("Bob", 35))

        when:
        def results = repository.findByNameAndAgeGreaterThanJson("Alice", 25)

        then:
        results.size() == 1
        results[0].name == "Alice"
        results[0].age == 30
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

    void "Criteria update uses the persisted name for a mapped property"() {
        given:
        def entity = new SnakeEntity(sessionId: "before", level: 7)
        snakeEntityRepository.save(entity)

        when:
        UpdateSpecification<SnakeEntity> setSessionId = (root, query, cb) -> {
            query.set(root.get("sessionId"), "after")
            return null
        }
        PredicateSpecification<SnakeEntity> bySessionId = (root, cb) ->
            cb.equal(root.get("sessionId"), "before")
        def updated = snakeEntityRepository.updateAll(setSessionId.where(bySessionId))

        then:
        updated == 1
        snakeEntityRepository.findSessionIdByLevel(7) == ["after"]
    }
}
