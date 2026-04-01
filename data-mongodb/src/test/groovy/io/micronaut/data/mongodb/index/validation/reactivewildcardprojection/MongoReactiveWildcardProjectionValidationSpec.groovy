package io.micronaut.data.mongodb.index.validation.reactivewildcardprojection

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.reactive.MongoSelectReactiveDriver
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoWildcardIndexed
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoReactiveWildcardProjectionValidationSpec extends Specification implements MongoSelectReactiveDriver {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.reactivewildcardprojection']
    }

    void 'fails fast for field-level wildcard projection in reactive mode'() {
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
interface InvalidReactiveWildcardProjectionEntityRepository extends CrudRepository<InvalidReactiveWildcardProjectionEntity, String> {
}

@MappedEntity('invalid_reactive_wildcard_projection_entities')
class InvalidReactiveWildcardProjectionEntity {
    @Id
    @GeneratedValue
    String id

    @MongoWildcardIndexed(name = 'invalid_reactive_wildcard_projection_idx', wildcardProjection = '{ "metadata.secret": 0 }')
    Map<String, Object> metadata
}
