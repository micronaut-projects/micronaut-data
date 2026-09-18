package io.micronaut.data.mongodb.index.validation.storageengine

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoStorageEngineValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.storageengine']
    }

    void 'fails fast for invalid storageEngine JSON'() {
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
interface InvalidStorageEngineEntityRepository extends CrudRepository<InvalidStorageEngineEntity, String> {
}

@MappedEntity('invalid_storage_engine_entities')
class InvalidStorageEngineEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'invalid_storage_engine_idx', storageEngine = '{ bad-json }')
    String name
}
