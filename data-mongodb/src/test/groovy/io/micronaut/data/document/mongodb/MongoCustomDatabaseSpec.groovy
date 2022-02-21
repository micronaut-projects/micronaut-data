package io.micronaut.data.document.mongodb

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(transactional = false)
class MongoCustomDatabaseSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    BalloonRepository balloonRepository = applicationContext.getBean(BalloonRepository)

    @Shared
    @Inject
    MongoClient mongoClient = applicationContext.getBean(MongoClient)

    def cleanup() {
        balloonRepository.deleteAll()
    }

    void 'test collections'() {
        when:
            Balloon b = new Balloon(name: "red")
        then:
            mongoClient.getDatabase("party").listCollectionNames().into(new ArrayList<>()) == []
        when:
            balloonRepository.save(b)
        then:
            mongoClient.getDatabase("party").listCollectionNames().into(new ArrayList<>()) == ["balloons"]
    }

}

@MongoRepository(databaseName = "party")
interface BalloonRepository extends CrudRepository<Balloon, String> {
}

@MappedEntity("balloons")
class Balloon {
    @Id
    @GeneratedValue
    String id
    String name
}