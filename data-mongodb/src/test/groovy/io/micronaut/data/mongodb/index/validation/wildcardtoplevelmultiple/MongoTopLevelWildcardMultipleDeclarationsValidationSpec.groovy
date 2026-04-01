package io.micronaut.data.mongodb.index.validation.wildcardtoplevelmultiple

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoWildcardIndex
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoTopLevelWildcardMultipleDeclarationsValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.wildcardtoplevelmultiple']
    }

    void 'allows multiple top-level wildcard declarations when wildcardProjection differs'() {
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
interface InvalidTopLevelWildcardMultipleEntityRepository extends CrudRepository<InvalidTopLevelWildcardMultipleEntity, String> {
}

@MongoWildcardIndex(name = 'invalid_top_level_wildcard_multiple_idx', wildcardProjection = '{ "metadata.secret": 0 }')
@MongoWildcardIndex(name = 'invalid_top_level_wildcard_multiple_other_idx', wildcardProjection = '{ "metadata.internal": 0 }')
@MappedEntity('invalid_top_level_wildcard_multiple_entities')
class InvalidTopLevelWildcardMultipleEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> metadata
}
