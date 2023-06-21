package io.micronaut.data.document.mongodb.reactive

import io.micronaut.data.document.mongodb.repositories.MongoBookRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.types.ObjectId
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class MongoReactiveAsSyncTransactionSpec extends Specification implements MongoSelectReactiveDriver {

    @Shared @Inject MongoBookRepository repository

    void 'test TX operation in test'() {
        when:
            repository.queryById(new ObjectId().toHexString())
        then:
            noExceptionThrown()
    }
}
