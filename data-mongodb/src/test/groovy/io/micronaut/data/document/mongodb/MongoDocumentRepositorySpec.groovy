package io.micronaut.data.document.mongodb

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.DeleteOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import groovy.transform.Memoized
import io.micronaut.data.document.mongodb.repositories.MongoAuthorRepository
import io.micronaut.data.document.mongodb.repositories.MongoDocumentRepository
import io.micronaut.data.document.mongodb.repositories.MongoExecutorPersonRepository
import io.micronaut.data.document.mongodb.repositories.MongoBasicTypesRepository
import io.micronaut.data.document.mongodb.repositories.MongoBookRepository
import io.micronaut.data.document.mongodb.repositories.MongoDomainEventsRepository
import io.micronaut.data.document.mongodb.repositories.MongoPersonRepository
import io.micronaut.data.document.mongodb.repositories.MongoSaleRepository
import io.micronaut.data.document.mongodb.repositories.MongoStudentRepository
import io.micronaut.data.document.tck.AbstractDocumentRepositorySpec
import io.micronaut.data.document.tck.entities.Document
import io.micronaut.data.document.tck.entities.Owner
import io.micronaut.data.document.tck.entities.Quantity
import io.micronaut.data.document.tck.entities.Sale
import io.micronaut.data.document.tck.repositories.AuthorRepository
import io.micronaut.data.document.tck.repositories.BasicTypesRepository
import io.micronaut.data.document.tck.repositories.BookRepository
import io.micronaut.data.document.tck.repositories.DomainEventsRepository
import io.micronaut.data.document.tck.repositories.SaleRepository
import io.micronaut.data.document.tck.repositories.StudentRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.mongodb.operations.options.MongoAggregationOptions
import io.micronaut.data.mongodb.operations.options.MongoFindOptions
import org.bson.BsonDocument

import java.util.stream.Collectors

import static io.micronaut.data.document.tck.repositories.DocumentRepository.Specifications.tagsArrayContains

class MongoDocumentRepositorySpec extends AbstractDocumentRepositorySpec implements MongoTestPropertyProvider {

    MongoClient mongoClient = context.getBean(MongoClient)

    void "test id mapping"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
            def people = personRepository.queryAll()
        then:
            people.every {it.size() == 5 && !it.id }
    }

    void "test attribute converter"() {
        when:
            def quantity = new Quantity(123)
            def sale = new Sale()
            sale.quantity = quantity
            saleRepository.save(sale)

            sale = saleRepository.findById(sale.getId()).get()
        then:
            sale.quantity
            sale.quantity.amount == 123

        when:
            def bsonSale = mongoClient.getDatabase("test").getCollection("sale", BsonDocument).find().first()
        then:
            bsonSale.size() == 2
            bsonSale.get("quantity").isInt32()

        when:
            saleRepository.updateQuantity(sale.id, new Quantity(345))
            sale = saleRepository.findById(sale.id).get()
        then:
            sale.quantity
            sale.quantity.amount == 345

        cleanup:
            saleRepository.deleteById(sale.id)
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

    void "test custom aggr"() {
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
            def people = personRepository.customAgg("J.*").toList()

        then:
            people.size() == 2
            people[0].name == "James"
            people[0].age == 0
            people[1].name == "Jeff"
            people[1].age == 0
    }

    void "test custom update"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
            personRepository.updateNamesCustom("Denis", "Dennis")
            def people = personRepository.findAll().toList()

        then:
            people.count { it.name == "Dennis"} == 0
            people.count { it.name == "Denis"} == 2
    }

    void "test custom update single"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
            def people = personRepository.findAll().toList()
            def jeff = people.find {it.name == "Jeff"}
            def updated = personRepository.updateCustomSingle(jeff)
            people = personRepository.findAll().toList()

        then:
            updated == 1
            people.count {it.name == "tom"} == 1
    }

    void "test custom update only names"() {
        when:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
            def people = personRepository.findAll().toList()
            people.forEach {it.age = 100 }
            personRepository.updateAll(people)
            people = personRepository.findAll().toList()

        then:
            people.size() == 4
            people.every{it.age > 0 }

        when:
            people.forEach() {
                it.name = it.name + " updated"
                it.age = -1
            }
            int updated = personRepository.updateCustomOnlyNames(people)
            people = personRepository.findAll().toList()

        then:
            updated == 4
            people.size() == 4
            people.every {it.name.endsWith(" updated") }
            people.every {it.age > 0 }
    }

    void "test custom delete"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
            def people = personRepository.findAll().toList()
            people.findAll {it.name == "Dennis"}.forEach{ it.name = "DoNotDelete"}
            def deleted = personRepository.deleteCustom(people)
            people = personRepository.findAll().toList()

        then:
            deleted == 2
            people.size() == 2
            people.count {it.name == "Dennis"}
    }

    void "test custom delete single"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
            def people = personRepository.findAll().toList()
            def jeff = people.find {it.name == "Jeff"}
            def deleted = personRepository.deleteCustomSingle(jeff)
            people = personRepository.findAll().toList()

        then:
            deleted == 1
            people.size() == 3

        when:
            def james = people.find {it.name == "James"}
            james.name = "DoNotDelete"
            deleted = personRepository.deleteCustomSingle(james)
            people = personRepository.findAll().toList()

        then:
            deleted == 0
            people.size() == 3
    }

    void "test custom delete single no entity"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
            def people = personRepository.findAll().toList()
            def jeff = people.find {it.name == "Jeff"}
            def deleted = personRepository.deleteCustomSingleNoEntity(jeff.getName())
            people = personRepository.findAll().toList()

        then:
            deleted == 1
            people.size() == 3
    }

    void "query executor counts"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
            def count = mongoExecutorPersonRepository.count(Filters.eq("name", "Jeff"))
        then:
            count == 1
        when:
            count = mongoExecutorPersonRepository.count(Filters.regex("name", /J.*/))
        then:
            count == 2
    }

    void "query executor finds"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
            def people = mongoExecutorPersonRepository.findAll(Filters.eq("name", "Jeff"))
        then:
            people.size() == 1
        when:
            people = mongoExecutorPersonRepository.findAll(new MongoFindOptions().filter(Filters.eq("name", "Jeff")))
        then:
            people.size() == 1
        when:
            people = mongoExecutorPersonRepository.findAll([Aggregates.match(Filters.eq("name", "Jeff"))])
        then:
            people.size() == 1
        when:
            people = mongoExecutorPersonRepository.findAll([Aggregates.match(Filters.eq("name", "Jeff"))], new MongoAggregationOptions())
        then:
            people.size() == 1
        when:
            def person = mongoExecutorPersonRepository.findOne(Filters.eq("name", "Jeff"))
        then:
            person.get().name == "Jeff"
        when:
            person = mongoExecutorPersonRepository.findOne(new MongoFindOptions().filter(Filters.eq("name", "Jeff")))
        then:
            person.get().name == "Jeff"
        when:
            person = mongoExecutorPersonRepository.findOne([Aggregates.match(Filters.eq("name", "Jeff"))])
        then:
            person.get().name == "Jeff"
        when:
            person = mongoExecutorPersonRepository.findOne([Aggregates.match(Filters.eq("name", "Jeff"))], new MongoAggregationOptions())
        then:
            person.get().name == "Jeff"
    }

    void "query executor finds page"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
            def people = mongoExecutorPersonRepository.findAll(Filters.regex("name", /J.*/), Pageable.from(0, 1))
        then:
            people.size() == 1
            people.getTotalPages() == 2
            people[0].name == "Jeff"
        when:
            people = mongoExecutorPersonRepository.findAll(Filters.regex("name", /J.*/), Pageable.from(1, 1))
        then:
            people.size() == 1
            people.getTotalPages() == 2
            people[0].name == "James"
        when:
            people = mongoExecutorPersonRepository.findAll(Filters.regex("name", /J.*/), Pageable.from(0, 1).order("name"))
        then:
            people.size() == 1
            people.getTotalPages() == 2
            people[0].name == "James"
        when:
            people = mongoExecutorPersonRepository.findAll(Filters.regex("name", /J.*/), Pageable.from(0, 2).order("name"))
        then:
            people.size() == 2
            people.getTotalPages() == 1
            people[0].name == "James"
            people[1].name == "Jeff"
        when:
            people = mongoExecutorPersonRepository.findAll(null, Pageable.from(0, 2).order("name"))
        then:
            people.size() == 2
            people.getTotalPages() == 2
        when:
            people = mongoExecutorPersonRepository.findAll(new MongoFindOptions()
                    .filter(Filters.regex("name", /J.*/))
                    .sort(Sorts.ascending("name")), Pageable.from(0, 2))
        then:
            people.size() == 2
            people.getTotalPages() == 1
            people[0].name == "James"
            people[1].name == "Jeff"
    }

    void "query executor deletes"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
            def people = mongoExecutorPersonRepository.findAll()
        then:
            people.size() == 4
        when:
            long deleted = mongoExecutorPersonRepository.deleteAll(Filters.regex("name", /J.*/))
        then:
            deleted == 2
        when:
            people = mongoExecutorPersonRepository.findAll()
        then:
            people.size() == 2
    }

    void "query executor deletes2"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
            def people = mongoExecutorPersonRepository.findAll()
        then:
            people.size() == 4
        when:
            long deleted = mongoExecutorPersonRepository.deleteAll(Filters.regex("name", /J.*/), new DeleteOptions())
        then:
            deleted == 2
        when:
            people = mongoExecutorPersonRepository.findAll()
        then:
            people.size() == 2
    }

    void "query executor updates"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
            def people = mongoExecutorPersonRepository.findAll()
        then:
            people.size() == 4
        when:
            long updated = mongoExecutorPersonRepository.updateAll(Filters.regex("name", /J.*/), Updates.set("name", "UPDATED"))
        then:
            updated == 2
        when:
            people = mongoExecutorPersonRepository.findAll()
        then:
            people.count{ it.name == "UPDATED" } == 2
    }

    void "query executor updates2"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
        when:
            def people = mongoExecutorPersonRepository.findAll()
        then:
            people.size() == 4
        when:
            long updated = mongoExecutorPersonRepository.updateAll(
                    Filters.regex("name", /J.*/),
                    Updates.set("name", "UPDATED"), new UpdateOptions()
            )
        then:
            updated == 2
        when:
            people = mongoExecutorPersonRepository.findAll()
        then:
            people.count{ it.name == "UPDATED" } == 2
    }

    void "test find by ids in"() {
        given:
            savePersons(["Joe", "Jennifer"])
        when:
            def people = personRepository.findAll()
            assert people.iterator().hasNext()
            def person = people.iterator().next()
            assert person != null
            def optPerson = personRepository.findById(person.id)
            def personsByIdIn = personRepository.findByIdIn(Arrays.asList(person.id))
            def personsByIdNotInIds = personRepository.findByIdNotIn(Arrays.asList(person.id)).stream().map(p -> p.id).collect(Collectors.toList())
        then:
            optPerson.present
            optPerson.get().id == person.id
            personsByIdIn.size() == 1
            personsByIdIn[0].id == person.id
            personsByIdNotInIds.size() > 0
            !personsByIdNotInIds.contains(person.id)
    }

    void "test find by multiple in params"() {
        given:
            def persons = savePersons(["Joe", "Jennifer"])
        when:
            // Make sure 2 records are created and ids assigned
            persons.size() == 2
            persons[0].id
            persons[1].id
            def personsByIdIn = personRepository.findByIdIn(Arrays.asList(persons[0].id))
            def multiplePersonsByNamesIn = personRepository.findByNameIn(Arrays.asList(persons[0].name, persons[1].name))
            def multiplePersonsByIdIn = personRepository.findByIdIn(Arrays.asList(persons[0].id, persons[1].id))
            def multiplePersonsByNamesInList = personRepository.findByNameInList([persons[0].name, persons[1].name] as String[])
            def personsByNamesInList = personRepository.findByNameInList([persons[0].name] as String[])
        then:
            personsByIdIn.size() == 1
            multiplePersonsByNamesIn.size() == 2
            multiplePersonsByIdIn.size() == 2
            multiplePersonsByNamesInList.size() == 2
            personsByNamesInList.size() == 1
    }

    void "test find by array contains"() {
        given:
        var doc1 = new Document()
        doc1.title = "Doc1"
        doc1.tags = ["red", "blue", "white"]
        documentRepository.save(doc1)
        var doc2 = new Document()
        doc2.title = "Doc2"
        doc2.tags = ["red", "blue"]
        documentRepository.save(doc2)
        var doc3 = new Document()
        doc3.title = "Doc3"
        documentRepository.save(doc3)
        when:
        def result1 = documentRepository.findByTagsArrayContains("red")
        def result2 = documentRepository.findByTagsArrayContains("grey")
        def result3 = documentRepository.findByTagsArrayContains(Arrays.asList("red", "blue"))
        def result4 = documentRepository.findByTagsArrayContains(Arrays.asList("red", "blue", "white"))
        def result5 = documentRepository.findByTagsArrayContains(Arrays.asList("red", "white"))
        def result6 = documentRepository.findByTagsArrayContains(Arrays.asList())
        then:
        result1.size() == 2
        result2.size() == 0
        result3.size() == 2
        result4.size() == 1
        result5.size() == 1
        result6.size() == 0
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
        var doc1 = new Document()
        doc1.title = "Doc1"
        doc1.tags = ["red", "blue", "white"]
        var owner1 = new Owner("Owner1")
        owner1.age = 40
        var owner2 = new Owner("Owner2")
        owner2.age = 30
        def owners = new HashMap<String, Owner>()
        owners["owner1"] = owner1
        owners["owner2"] = owner2
        doc1.owners = owners
        documentRepository.save(doc1)
        when:
        var optDoc = documentRepository.findById(doc1.id)
        then:
        optDoc.present
        def doc = optDoc.get()
        doc.owners.size() == 2
        doc.owners["owner1"].name == "Owner1"
        doc.owners["owner1"].age == 40
        doc.owners["owner2"].name == "Owner2"
        doc.owners["owner2"].age == 30
        cleanup:
        documentRepository.deleteAll()
    }

    @Memoized
    MongoExecutorPersonRepository getMongoExecutorPersonRepository() {
        return context.getBean(MongoExecutorPersonRepository)
    }

    @Memoized
    @Override
    BasicTypesRepository getBasicTypeRepository() {
        return context.getBean(MongoBasicTypesRepository)
    }

    @Memoized
    @Override
    MongoPersonRepository getPersonRepository() {
        return context.getBean(MongoPersonRepository)
    }

    @Memoized
    @Override
    BookRepository getBookRepository() {
        return context.getBean(MongoBookRepository)
    }

    @Memoized
    @Override
    AuthorRepository getAuthorRepository() {
        return context.getBean(MongoAuthorRepository)
    }

    @Memoized
    @Override
    StudentRepository getStudentRepository() {
        return context.getBean(MongoStudentRepository)
    }

    @Memoized
    @Override
    SaleRepository getSaleRepository() {
        return context.getBean(MongoSaleRepository)
    }

    @Memoized
    @Override
    DomainEventsRepository getEventsRepository() {
        return context.getBean(MongoDomainEventsRepository)
    }

    @Memoized
    @Override
    MongoDocumentRepository getDocumentRepository() {
        return context.getBean(MongoDocumentRepository)
    }
}
