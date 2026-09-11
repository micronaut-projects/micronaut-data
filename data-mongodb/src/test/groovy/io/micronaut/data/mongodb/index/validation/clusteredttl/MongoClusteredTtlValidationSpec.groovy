package io.micronaut.data.mongodb.index.validation.clusteredttl

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoClusteredIndex
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoClusteredTtlValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.clusteredttl']
    }

    void 'fails fast when clustered TTL uses non date id type'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('requires a date/time identity type')
    }
}

@MongoClusteredIndex(name = 'invalid_clustered_ttl_idx', expireAfterSeconds = 300)
@MappedEntity('invalid_clustered_ttl_entities')
class InvalidClusteredTtlEntity {
    @Id
    String id

    String name
}
