package io.micronaut.data.document.mongodb.validation.reactivewildcardtoplevelmultiple

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.reactive.MongoSelectReactiveDriver
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoWildcardIndex
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoReactiveTopLevelWildcardMultipleDeclarationsValidationSpec extends Specification implements MongoSelectReactiveDriver {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.reactivewildcardtoplevelmultiple']
    }

    void 'fails fast when reactive multiple top-level wildcard declarations conflict on options'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('declare conflicting options for key [$**]')
    }
}

@MongoRepository
interface InvalidReactiveTopLevelWildcardMultipleEntityRepository extends CrudRepository<InvalidReactiveTopLevelWildcardMultipleEntity, String> {
}

@MongoWildcardIndex(name = 'invalid_reactive_top_level_wildcard_multiple_idx', wildcardProjection = '{ "metadata.secret": 0 }')
@MongoWildcardIndex(name = 'invalid_reactive_top_level_wildcard_multiple_idx', wildcardProjection = '{ "metadata.internal": 0 }')
@MappedEntity('invalid_reactive_top_level_wildcard_multiple_entities')
class InvalidReactiveTopLevelWildcardMultipleEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> metadata
}
