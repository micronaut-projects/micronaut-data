package io.micronaut.data.document.mongodb


import com.mongodb.client.MongoClient
import groovy.transform.Memoized
import io.micronaut.data.document.mongodb.repositories.MongoAuthorRepository
import io.micronaut.data.document.mongodb.repositories.MongoBasicTypesRepository
import io.micronaut.data.document.mongodb.repositories.MongoBookRepository
import io.micronaut.data.document.mongodb.repositories.MongoDomainEventsRepository
import io.micronaut.data.document.mongodb.repositories.MongoPersonRepository
import io.micronaut.data.document.mongodb.repositories.MongoSaleRepository
import io.micronaut.data.document.mongodb.repositories.MongoStudentRepository
import io.micronaut.data.document.tck.AbstractDocumentRepositorySpec
import io.micronaut.data.document.tck.entities.Quantity
import io.micronaut.data.document.tck.entities.Sale
import io.micronaut.data.document.tck.repositories.AuthorRepository
import io.micronaut.data.document.tck.repositories.BasicTypesRepository
import io.micronaut.data.document.tck.repositories.BookRepository
import io.micronaut.data.document.tck.repositories.DomainEventsRepository
import io.micronaut.data.document.tck.repositories.PersonRepository
import io.micronaut.data.document.tck.repositories.SaleRepository
import io.micronaut.data.document.tck.repositories.StudentRepository
import org.bson.BsonDocument

class MongoDocumentRepositorySpec extends AbstractDocumentRepositorySpec implements MongoTestPropertyProvider {

    MongoClient mongoClient = context.getBean(MongoClient)

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

    @Memoized
    @Override
    BasicTypesRepository getBasicTypeRepository() {
        return context.getBean(MongoBasicTypesRepository)
    }

    @Memoized
    @Override
    PersonRepository getPersonRepository() {
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
}
