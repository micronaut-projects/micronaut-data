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

    void "test reactive query executor custom update returning after and before"() {
        given:
        def person = personRepository.save("Jeff", 20)

        when:
        def updated = mongoReactiveExecutorPersonRepository.updateCustomReturning(person.id, "Updated").block()

        then:
        updated != null
        updated.id == person.id
        updated.name == "Updated"
        personRepository.findById(person.id).get().name == "Updated"

        when:
        def previous = mongoReactiveExecutorPersonRepository.updateCustomReturningBefore(person.id, "Updated Again").block()

        then:
        previous != null
        previous.id == person.id
        previous.name == "Updated"
        personRepository.findById(person.id).get().name == "Updated Again"
    }

    void "test reactive query executor custom update returning no match"() {
        expect:
        mongoReactiveExecutorPersonRepository.updateCustomReturning("507f1f77bcf86cd799439011", "Updated").block() == null
    }

    void "test reactive query executor custom update returning default returnDocument is BEFORE"() {
        given:
        def person = personRepository.save("Jeff Default", 20)

        when:
        def previous = mongoReactiveExecutorPersonRepository.updateCustomReturningDefault(person.id, "Jeff Default Updated").block()

        then:
        previous != null
        previous.id == person.id
        previous.name == "Jeff Default"
        personRepository.findById(person.id).get().name == "Jeff Default Updated"
    }

    void "test reactive query executor annotation returnDocument takes precedence over options returnDocument"() {
        given:
        def person = personRepository.save("Jeff Options", 20)
        def options = new com.mongodb.client.model.FindOneAndUpdateOptions().returnDocument(com.mongodb.client.model.ReturnDocument.BEFORE)

        when:
        def result = mongoReactiveExecutorPersonRepository.updateCustomReturningAfterWithOptions(person.id, "Jeff Options Updated", options).block()

        then:
        result != null
        result.name == "Jeff Options Updated"
        personRepository.findById(person.id).get().name == "Jeff Options Updated"
    }

    void "test reactive query executor update returning options parameter is applied while annotation returnDocument wins"() {
        given:
        def id = "507f1f77bcf86cd799439012"
        def options = new com.mongodb.client.model.FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(com.mongodb.client.model.ReturnDocument.BEFORE)

        when:
        def result = mongoReactiveExecutorPersonRepository.updateCustomReturningAfterWithOptions(id, "Inserted Via Options", options).block()

        then:
        result != null
        result.name == "Inserted Via Options"
        personRepository.findById(id).present
    }

    void "test reactive query executor custom update returning dto projection"() {
        given:
        def person = personRepository.save("Jeff DTO", 20)

        when:
        def updated = mongoReactiveExecutorPersonRepository.updateCustomReturningDto(person.id, "Jeff DTO Updated").block()

        then:
        updated != null
        updated.name() == "Jeff DTO Updated"
        personRepository.findById(person.id).get().name == "Jeff DTO Updated"
    }

    void "test reactive query executor custom update returning scalar projection"() {
        given:
        def person = personRepository.save("Jeff Scalar", 20)

        when:
        def updatedName = mongoReactiveExecutorPersonRepository.updateCustomReturningNameProjection(person.id, "Jeff Scalar Updated").block()

        then:
        updatedName == "Jeff Scalar Updated"
        personRepository.findById(person.id).get().name == "Jeff Scalar Updated"
    }

    void "test reactive query executor custom update returning uses sort to select matched document"() {
        given:
        personRepository.save("Sorted Candidate", 40)
        personRepository.save("Sorted Candidate", 20)

        when:
        def returnedBefore = mongoReactiveExecutorPersonRepository.updateCustomReturningSortedBefore("Sorted Candidate", "Sorted Updated").block()

        then:
        returnedBefore != null
        returnedBefore.age == 20
        returnedBefore.name == "Sorted Candidate"
        personRepository.findByName("Sorted Updated")*.age == [20]
        personRepository.findByName("Sorted Candidate")*.age.sort() == [40]
    }

}
