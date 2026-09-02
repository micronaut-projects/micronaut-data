package io.micronaut.data.mongodb.index.validation.invalidpath

import io.micronaut.context.ApplicationContext
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoCompoundIndexValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.invalidpath']
    }


    void 'fails fast for invalid compound index path'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Invalid Mongo index path [missing]')
    }
}

@MongoRepository
interface InvalidCompoundIndexedEntityRepository extends CrudRepository<InvalidCompoundIndexedEntity, String> {
}

@MongoCompoundIndex(
        name = 'invalid_idx',
        fields = [
                @MongoCompoundIndexField(value = 'missing', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('invalid_compound_indexed_entities')
class InvalidCompoundIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name
}
