package io.micronaut.data.nitrite.repository

import io.micronaut.data.nitrite.model.CriteriaPerson
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import static io.micronaut.data.repository.jpa.criteria.PredicateSpecification.where

@MicronautTest(transactional = false)
class CriteriaPersonRepositorySpec extends Specification {

    @Inject
    CriteriaPersonRepository repository

    def setup() {
        repository.deleteAll()
        repository.saveAll([
                new CriteriaPerson("Denis", 13),
                new CriteriaPerson("Josh", 22)
        ])
    }

    void "criteria find/count/update/delete works"() {
        when:
        PredicateSpecification<CriteriaPerson> denis = (root, cb) -> cb.equal(root.get("name"), "Denis")
        PredicateSpecification<CriteriaPerson> ageLessThan20 = (root, cb) -> cb.lessThan(root.get("age"), 20)
        def found = repository.findOne(denis).orElse(null)
        def countAll = repository.count((PredicateSpecification<CriteriaPerson>) null)
        def countAgeLt20 = repository.count(ageLessThan20)

        then:
        found != null
        found.name == "Denis"
        countAll == 2
        countAgeLt20 == 1

        when:
        UpdateSpecification<CriteriaPerson> setName = (root, query, cb) -> {
            query.set(root.get("name"), "Steven")
            return null
        }
        def updated = repository.updateAll(setName.where(denis))

        then:
        updated == 1
        repository.findOne(where((root, cb) -> cb.equal(root.get("name"), "Steven"))).isPresent()

        when:
        PredicateSpecification<CriteriaPerson> josh = (root, cb) -> cb.equal(root.get("name"), "Josh")
        def deleted = repository.deleteAll(where(josh))

        then:
        deleted == 1
    }
}
