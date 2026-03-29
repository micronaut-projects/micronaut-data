package io.micronaut.data.document.mongodb.ttl

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoTtlIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.ttl']
    }

    Class<?> expectedCollectionsCreatorBeanType() {
        io.micronaut.data.mongodb.init.MongoCollectionsCreator
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates field TTL index'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'ttl_indexed_entities')
            assert indexes*.name.contains('expires_at_ttl_idx')
            def index = indexes.find { it.name == 'expires_at_ttl_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'expires_at'
            assert index.fields[0].order() == 1
            assert index.expireAfterSeconds == 60
        }
    }
}

@MongoRepository
interface TtlIndexedEntityRepository extends CrudRepository<TtlIndexedEntity, String> {
}

@MappedEntity('ttl_indexed_entities')
class TtlIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'expires_at_ttl_idx', expireAfterSeconds = 60)
    java.time.Instant expiresAt
}
