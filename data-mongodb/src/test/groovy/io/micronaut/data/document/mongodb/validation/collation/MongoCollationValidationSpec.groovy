package io.micronaut.data.document.mongodb.validation.collation

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoCollationValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.collation']
    }

    void 'fails fast for invalid collation JSON'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('JSON reader')
    }
}

@MongoRepository
interface InvalidCollationIndexedEntityRepository extends CrudRepository<InvalidCollationIndexedEntity, String> {
}

@MappedEntity('invalid_collation_indexed_entities')
class InvalidCollationIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'invalid_collation_idx', collation = '{ invalid json }')
    String name
}
