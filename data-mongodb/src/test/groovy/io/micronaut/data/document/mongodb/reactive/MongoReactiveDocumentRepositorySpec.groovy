package io.micronaut.data.document.mongodb.reactive

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.DeleteOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import groovy.transform.Memoized
import io.micronaut.data.document.mongodb.MongoDocumentRepositorySpec
import io.micronaut.data.document.mongodb.repositories.MongoReactiveExecutorPersonRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.mongodb.operations.options.MongoAggregationOptions
import io.micronaut.data.mongodb.operations.options.MongoFindOptions

class MongoReactiveDocumentRepositorySpec extends MongoDocumentRepositorySpec implements MongoSelectReactiveDriver {

    @Memoized
    MongoReactiveExecutorPersonRepository getMongoReactiveExecutorPersonRepository() {
        return context.getBean(MongoReactiveExecutorPersonRepository)
    }

    void "test reactive query executor counts"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
        def count = mongoReactiveExecutorPersonRepository.count(Filters.eq("name", "Jeff")).block()
        then:
        count == 1
        when:
        count = mongoReactiveExecutorPersonRepository.count(Filters.regex("name", /J.*/)).block()
        then:
        count == 2
    }

    void "test reactive query executor finds"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
        def people = mongoReactiveExecutorPersonRepository.findAll(Filters.eq("name", "Jeff")).collectList().block()
        then:
        people.size() == 1
        when:
        people = mongoReactiveExecutorPersonRepository.findAll(new MongoFindOptions().filter(Filters.eq("name", "Jeff"))).collectList().block()
        then:
        people.size() == 1
        when:
        people = mongoReactiveExecutorPersonRepository.findAll([Aggregates.match(Filters.eq("name", "Jeff"))]).collectList().block()
        then:
        people.size() == 1
        when:
        people = mongoReactiveExecutorPersonRepository.findAll([Aggregates.match(Filters.eq("name", "Jeff"))], new MongoAggregationOptions()).collectList().block()
        then:
        people.size() == 1
        when:
        def person = mongoReactiveExecutorPersonRepository.findOne(Filters.eq("name", "Jeff"))
        then:
        person.block().name == "Jeff"
        when:
        person = mongoReactiveExecutorPersonRepository.findOne(new MongoFindOptions().filter(Filters.eq("name", "Jeff")))
        then:
        person.block().name == "Jeff"
        when:
        person = mongoReactiveExecutorPersonRepository.findOne([Aggregates.match(Filters.eq("name", "Jeff"))])
        then:
        person.block().name == "Jeff"
        when:
        person = mongoReactiveExecutorPersonRepository.findOne([Aggregates.match(Filters.eq("name", "Jeff"))], new MongoAggregationOptions())
        then:
        person.block().name == "Jeff"
    }

    void "test reactive query executor finds page"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
        def people = mongoReactiveExecutorPersonRepository.findAll(Filters.regex("name", /J.*/), Pageable.from(0, 1)).block()
        then:
        people.size() == 1
        people.getTotalPages() == 2
        people[0].name == "Jeff"
        when:
        people = mongoReactiveExecutorPersonRepository.findAll(Filters.regex("name", /J.*/), Pageable.from(1, 1)).block()
        then:
        people.size() == 1
        people.getTotalPages() == 2
        people[0].name == "James"
        when:
        people = mongoReactiveExecutorPersonRepository.findAll(Filters.regex("name", /J.*/), Pageable.from(0, 1).order("name")).block()
        then:
        people.size() == 1
        people.getTotalPages() == 2
        people[0].name == "James"
        when:
        people = mongoReactiveExecutorPersonRepository.findAll(Filters.regex("name", /J.*/), Pageable.from(0, 2).order("name")).block()
        then:
        people.size() == 2
        people.getTotalPages() == 1
        people[0].name == "James"
        people[1].name == "Jeff"
        when:
        people = mongoReactiveExecutorPersonRepository.findAll(null, Pageable.from(0, 2).order("name")).block()
        then:
        people.size() == 2
        people.getTotalPages() == 2
        when:
        people = mongoReactiveExecutorPersonRepository.findAll(new MongoFindOptions()
                .filter(Filters.regex("name", /J.*/))
                .sort(Sorts.ascending("name")), Pageable.from(0, 2)).block()
        then:
        people.size() == 2
        people.getTotalPages() == 1
        people[0].name == "James"
        people[1].name == "Jeff"
    }

    void "test reactive query executor deletes"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
        def people = mongoReactiveExecutorPersonRepository.findAll().collectList().block()
        then:
        people.size() == 4
        when:
        long deleted = mongoReactiveExecutorPersonRepository.deleteAll(Filters.regex("name", /J.*/)).block()
        then:
        deleted == 2
        when:
        people = mongoReactiveExecutorPersonRepository.findAll().collectList().block()
        then:
        people.size() == 2
    }

    void "test reactive query executor deletes2"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
        def people = mongoReactiveExecutorPersonRepository.findAll().collectList().block()
        then:
        people.size() == 4
        when:
        long deleted = mongoReactiveExecutorPersonRepository.deleteAll(Filters.regex("name", /J.*/), new DeleteOptions()).block()
        then:
        deleted == 2
        when:
        people = mongoReactiveExecutorPersonRepository.findAll().collectList().block()
        then:
        people.size() == 2
    }

    void "test reactive query executor updates"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
        def people = mongoReactiveExecutorPersonRepository.findAll().collectList().block()
        then:
        people.size() == 4
        when:
        long updated = mongoReactiveExecutorPersonRepository.updateAll(Filters.regex("name", /J.*/), Updates.set("name", "UPDATED")).block()
        then:
        updated == 2
        when:
        people = mongoReactiveExecutorPersonRepository.findAll().collectList().block()
        then:
        people.count{ it.name == "UPDATED" } == 2
    }

    void "test reactive query executor updates2"() {
        given:
        savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
        def people = mongoReactiveExecutorPersonRepository.findAll().collectList().block()
        then:
        people.size() == 4
        when:
        long updated = mongoReactiveExecutorPersonRepository.updateAll(
                Filters.regex("name", /J.*/),
                Updates.set("name", "UPDATED"), new UpdateOptions()
        ).block()
        then:
        updated == 2
        when:
        people = mongoReactiveExecutorPersonRepository.findAll().collectList().block()
        then:
        people.count{ it.name == "UPDATED" } == 2
    }

}
