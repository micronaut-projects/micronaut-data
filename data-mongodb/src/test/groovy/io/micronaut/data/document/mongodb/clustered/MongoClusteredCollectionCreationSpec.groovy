package io.micronaut.data.document.mongodb.clustered

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoClusteredIndex
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.Document
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoClusteredCollectionCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.clustered']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates clustered collection with configured name'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(io.micronaut.data.mongodb.init.MongoCollectionsCreator)
        conditions.eventually {
            def collection = mongoClient.getDatabase('test').listCollections().into([]).find { it.getString('name') == 'clustered_indexed_entities' }
            assert collection != null
            def options = collection.get('options', Document)
            assert options != null
            def clustered = options.get('clusteredIndex', Document)
            assert clustered != null
            assert clustered.get('key', Document).getInteger('_id') == 1
            assert clustered.getBoolean('unique')
            assert clustered.getString('name') == 'clustered_idx'
        }
    }
}

@MongoRepository
interface ClusteredIndexedEntityRepository extends CrudRepository<ClusteredIndexedEntity, java.time.Instant> {
}

@MongoClusteredIndex(name = 'clustered_idx')
@MappedEntity('clustered_indexed_entities')
class ClusteredIndexedEntity {
    @Id
    java.time.Instant id

    String name
}
