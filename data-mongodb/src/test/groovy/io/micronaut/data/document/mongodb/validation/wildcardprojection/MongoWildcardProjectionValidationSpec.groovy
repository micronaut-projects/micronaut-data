package io.micronaut.data.document.mongodb.validation.wildcardprojection

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoWildcardIndexed
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoWildcardProjectionValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.wildcardprojection']
    }

    void 'fails fast for field-level wildcard projection'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('field-level @MongoWildcardIndexed is not supported by MongoDB')
    }
}

@MongoRepository
interface InvalidWildcardProjectionEntityRepository extends CrudRepository<InvalidWildcardProjectionEntity, String> {
}

@MappedEntity('invalid_wildcard_projection_entities')
class InvalidWildcardProjectionEntity {
    @Id
    @GeneratedValue
    String id

    @MongoWildcardIndexed(name = 'invalid_wildcard_projection_idx', wildcardProjection = '{ "metadata.secret": 0 }')
    Map<String, Object> metadata
}
