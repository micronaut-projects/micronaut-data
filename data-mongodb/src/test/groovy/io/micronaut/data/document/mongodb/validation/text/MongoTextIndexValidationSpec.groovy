package io.micronaut.data.document.mongodb.validation.text

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoTextIndexed
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoTextIndexValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.text']
    }

    void 'fails fast for invalid text weight'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.cause != null
        e.cause.message.contains('Mongo text index weight must be greater than zero')
    }
}

@MongoRepository
interface InvalidTextWeightEntityRepository extends CrudRepository<InvalidTextWeightEntity, String> {
}

@MappedEntity('invalid_text_weight_entities')
class InvalidTextWeightEntity {
    @Id
    @GeneratedValue
    String id

    @MongoTextIndexed(name = 'invalid_text_weight_idx', weight = 0)
    String name
}
