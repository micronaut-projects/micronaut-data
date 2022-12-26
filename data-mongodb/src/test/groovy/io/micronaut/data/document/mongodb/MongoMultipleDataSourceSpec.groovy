package io.micronaut.data.document.mongodb

import groovy.transform.CompileStatic
import io.micronaut.aop.InvocationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.type.Argument
import io.micronaut.data.document.mongodb.repositories.MongoPersonRepository
import io.micronaut.data.document.tck.entities.Person
import io.micronaut.data.model.runtime.InsertOperation
import io.micronaut.data.model.runtime.StoredQuery
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.operations.MongoRepositoryOperations
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.annotation.TransactionalAdvice
import jakarta.inject.Inject
import jakarta.inject.Named
import org.bson.UuidRepresentation
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Specification

import javax.transaction.Transactional

@Property(name = "spec.name", value = "MongoMultipleDataSourceSpec")
@MicronautTest(transactional = false)
class MongoMultipleDataSourceSpec extends Specification implements TestPropertyProvider {

    @Inject
    XyzMongoPersonRepository personRepository
    @Inject
    OtherPersonRepository otherPersonRepository

    void "test multiple data sources"() {
        when:
            personRepository.save(new Person(name: "Fred"))
            personRepository.save(new Person(name: "Bob"))

        then:
            personRepository.count() == 2
            otherPersonRepository.count() == 0

        when:
            otherPersonRepository.save(new Person(name: "Joe"))

        then:
            otherPersonRepository.findAll().toList()[0].name == "Joe"

        when:
            otherPersonRepository.saveTwoOtherDb(
                    new Person(name: "One"),
                    new Person(name: "Two")
            )
            otherPersonRepository.saveTwoOtherDb2(
                    new Person(name: "Three"),
                    new Person(name: "Four")
            )

        then:
            otherPersonRepository.count() == 5
    }

    static MongoDBContainer mongoDBContainer1
    static MongoDBContainer mongoDBContainer2

    def cleanupSpec() {
        mongoDBContainer1.stop()
        mongoDBContainer2.stop()
    }

    @Override
    Map<String, String> getProperties() {
        mongoDBContainer1 = new MongoDBContainer(DockerImageName.parse("mongo").withTag("5"))
        mongoDBContainer2 = new MongoDBContainer(DockerImageName.parse("mongo").withTag("5"))
        mongoDBContainer1.start()
        mongoDBContainer2.start()
        return [
                'mongodb.servers.xyz.uri': mongoDBContainer1.replicaSetUrl,
                'mongodb.servers.other.uri': mongoDBContainer2.replicaSetUrl,
                'mongodb.uuid-representation': UuidRepresentation.STANDARD.name(),
                'mongodb.servers.xyz.package-names': ['io.micronaut.data'],
                'mongodb.servers.other.package-names': ['io.micronaut.data']
        ]
    }

    @Requires(property = "spec.name", value = "MongoMultipleDataSourceSpec")
    @MongoRepository('xyz')
    static interface XyzMongoPersonRepository extends MongoPersonRepository {
    }

    @Requires(property = "spec.name", value = "MongoMultipleDataSourceSpec")
    @MongoRepository('other')
    static abstract class OtherPersonRepository implements CrudRepository<Person, String> {

        private final MongoRepositoryOperations mongoRepositoryOperations;

        OtherPersonRepository(@Named("other") MongoRepositoryOperations mongoRepositoryOperations) {
            this.mongoRepositoryOperations = mongoRepositoryOperations;
        }

        @Transactional
        @TransactionalAdvice("other")
        void saveTwoOtherDb(Person one, Person two) {
            saveTwo(one, two)
        }

        @Transactional
        @TransactionalAdvice(transactionManager = "other")
        void saveTwoOtherDb2(Person one, Person two) {
            saveTwo(one, two)
        }

        void saveTwo(Person one, Person two) {
            mongoRepositoryOperations.persist(insertOperation(one))
            mongoRepositoryOperations.persist(insertOperation(two))
        }

        @CompileStatic
        <T> InsertOperation<T> insertOperation(T instance) {
            return new InsertOperation<T>() {
                @Override
                T getEntity() {
                    return instance
                }

                @Override
                Class<T> getRootEntity() {
                    return instance.getClass() as Class<T>
                }

                @Override
                Class<?> getRepositoryType() {
                    return Object.class
                }

                @Override
                StoredQuery<T, ?> getStoredQuery() {
                    return null
                }

                @Override
                InvocationContext<?, ?> getInvocationContext() {
                    return null
                }

                @Override
                String getName() {
                    return instance.getClass().name
                }

                @Override
                ConvertibleValues<Object> getAttributes() {
                    return ConvertibleValues.EMPTY
                }

                @Override
                Argument<T> getResultArgument() {
                    return Argument.of(instance.getClass()) as Argument<T>
                }
            }
        }
    }
}
