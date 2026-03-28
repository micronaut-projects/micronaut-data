package io.micronaut.data.document.mongodb.validation.reactivestorageengine

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.reactive.MongoSelectReactiveDriver
import io.micronaut.data.mongodb.annotation.MongoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoReactiveStorageEngineValidationSpec extends Specification implements MongoSelectReactiveDriver {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.reactivestorageengine']
    }

    void 'fails fast for invalid storageEngine JSON in reactive mode'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Mongo storageEngine for entity')
        e.message.contains('must be valid JSON')
    }
}

@MongoRepository
interface InvalidReactiveStorageEngineEntityRepository extends CrudRepository<InvalidReactiveStorageEngineEntity, String> {
}

@MappedEntity('invalid_reactive_storage_engine_entities')
class InvalidReactiveStorageEngineEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'invalid_reactive_storage_engine_idx', storageEngine = '{ bad-json }')
    String name
}
