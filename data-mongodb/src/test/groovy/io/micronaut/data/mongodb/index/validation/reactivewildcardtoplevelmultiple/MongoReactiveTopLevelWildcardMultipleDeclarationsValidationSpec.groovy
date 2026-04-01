package io.micronaut.data.mongodb.index.validation.reactivewildcardtoplevelmultiple

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.reactive.MongoSelectReactiveDriver
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoWildcardIndex
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoReactiveTopLevelWildcardMultipleDeclarationsValidationSpec extends Specification implements MongoSelectReactiveDriver {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.reactivewildcardtoplevelmultiple']
    }

    void 'allows reactive multiple top-level wildcard declarations when wildcardProjection differs'() {
        when:
        def context = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        noExceptionThrown()

        cleanup:
        context?.close()
    }
}

@MongoRepository
interface InvalidReactiveTopLevelWildcardMultipleEntityRepository extends CrudRepository<InvalidReactiveTopLevelWildcardMultipleEntity, String> {
}

@MongoWildcardIndex(name = 'invalid_reactive_top_level_wildcard_multiple_idx', wildcardProjection = '{ "metadata.secret": 0 }')
@MongoWildcardIndex(name = 'invalid_reactive_top_level_wildcard_multiple_other_idx', wildcardProjection = '{ "metadata.internal": 0 }')
@MappedEntity('invalid_reactive_top_level_wildcard_multiple_entities')
class InvalidReactiveTopLevelWildcardMultipleEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> metadata
}
