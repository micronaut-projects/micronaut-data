package io.micronaut.data.document.mongodb.validation.partialfilter

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

class MongoCompoundIndexPartialFilterValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.partialfilter']
    }

    void 'fails fast when sparse and partialFilterExpression are both defined on a compound index'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('cannot define both sparse and partialFilterExpression')
    }
}

@MongoRepository
interface PartialFilterCompoundIndexedEntityRepository extends CrudRepository<PartialFilterCompoundIndexedEntity, String> {
}

@MongoCompoundIndex(
        name = 'partial_filter_idx',
        sparse = true,
        partialFilterExpression = '{ "name": { "$exists": true } }',
        fields = [
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('partial_filter_compound_indexed_entities')
class PartialFilterCompoundIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name
}
