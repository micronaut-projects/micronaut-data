package io.micronaut.data.mongodb.index.validation.hashed

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoCompoundHashedIndexValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.hashed']
    }

    void 'fails fast when compound hashed index is declared unique'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Mongo compound hashed index')
        e.message.contains('cannot be unique')
    }
}

@MongoRepository
interface InvalidCompoundHashedIndexEntityRepository extends CrudRepository<InvalidCompoundHashedIndexEntity, String> {
}

@MongoCompoundIndex(
        name = 'invalid_unique_hashed_idx',
        unique = true,
        fields = [
                @MongoCompoundIndexField(value = 'tenantId'),
                @MongoCompoundIndexField(value = 'accountId', hashed = true)
        ]
)
@MappedEntity('invalid_compound_hashed_index_entities')
class InvalidCompoundHashedIndexEntity {
    @Id
    @GeneratedValue
    String id

    String tenantId

    String accountId
}
