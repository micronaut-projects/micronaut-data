package io.micronaut.data.document.mongodb.validation.duplicatefield

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

class MongoCompoundIndexDuplicateFieldValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.duplicatefield']
    }


    void 'fails fast for duplicate compound index field entries'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Duplicate Mongo index path [name]')
    }
}

@MongoRepository
interface DuplicateCompoundIndexedEntityRepository extends CrudRepository<DuplicateCompoundIndexedEntity, String> {
}

@MongoCompoundIndex(
        name = 'duplicate_idx',
        fields = [
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC),
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.DESC)
        ]
)
@MappedEntity('duplicate_compound_indexed_entities')
class DuplicateCompoundIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name
}
