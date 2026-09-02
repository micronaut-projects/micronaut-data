package io.micronaut.data.mongodb.index.validation.emptyfields

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoCompoundIndexEmptyFieldsValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.emptyfields']
    }

    void 'fails fast for empty compound index field list'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('must declare at least one field')
    }
}

@MongoRepository
interface EmptyFieldsCompoundIndexedEntityRepository extends CrudRepository<EmptyFieldsCompoundIndexedEntity, String> {
}

@MongoCompoundIndex(name = 'empty_idx', fields = [])
@MappedEntity('empty_fields_compound_indexed_entities')
class EmptyFieldsCompoundIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name
}
