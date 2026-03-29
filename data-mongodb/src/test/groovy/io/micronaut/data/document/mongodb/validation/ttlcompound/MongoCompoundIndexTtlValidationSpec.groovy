package io.micronaut.data.document.mongodb.validation.ttlcompound

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoCompoundIndexTtlValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.ttlcompound']
    }

    void 'fails fast for TTL on compound index'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('TTL')
    }
}

@MongoRepository
interface TtlCompoundIndexedEntityRepository extends CrudRepository<TtlCompoundIndexedEntity, String> {
}

@MongoCompoundIndex(
        name = 'ttl_compound_idx',
        expireAfterSeconds = 60,
        fields = [
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC),
                @MongoCompoundIndexField(value = 'age', direction = MongoIndexDirection.DESC)
        ]
)
@MappedEntity('ttl_compound_indexed_entities')
class TtlCompoundIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name

    Integer age
}
