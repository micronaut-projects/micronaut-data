package io.micronaut.data.document.mongodb.validation.wildcardtoplevelmultiple

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoWildcardIndex
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoTopLevelWildcardMultipleDeclarationsValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.wildcardtoplevelmultiple']
    }

    void 'fails fast when multiple top-level wildcard declarations conflict on options'() {
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
interface InvalidTopLevelWildcardMultipleEntityRepository extends CrudRepository<InvalidTopLevelWildcardMultipleEntity, String> {
}

@MongoWildcardIndex(name = 'invalid_top_level_wildcard_multiple_idx', wildcardProjection = '{ "metadata.secret": 0 }')
@MongoWildcardIndex(name = 'invalid_top_level_wildcard_multiple_idx', wildcardProjection = '{ "metadata.internal": 0 }')
@MappedEntity('invalid_top_level_wildcard_multiple_entities')
class InvalidTopLevelWildcardMultipleEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> metadata
}
