package io.micronaut.data.mongodb.index.validation.clusteredttlvalid

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoClusteredIndex
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

import java.time.ZonedDateTime

class MongoClusteredTtlValidSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.clusteredttlvalid']
    }

    void 'starts when clustered TTL uses ZonedDateTime id type'() {
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
interface ValidClusteredTtlEntityRepository extends CrudRepository<ValidClusteredTtlEntity, ZonedDateTime> {
}

@MongoClusteredIndex(name = 'valid_clustered_ttl_idx', expireAfterSeconds = 300)
@MappedEntity('valid_clustered_ttl_entities')
class ValidClusteredTtlEntity {
    @Id
    ZonedDateTime id

    String name
}
