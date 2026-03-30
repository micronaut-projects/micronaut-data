package io.micronaut.data.document.mongodb.simple

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

class MongoIndexCreationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.simple']
    }

    Class<?> expectedCollectionsCreatorBeanType() {
        io.micronaut.data.mongodb.init.MongoCollectionsCreator
    }
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates declared indexes for existing collections'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'indexed_entities')
            assert indexes*.name.contains('name_idx')
            def index = indexes.find { it.name == 'name_idx' }
            assert index.unique
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'name'
            assert index.fields[0].order() == 1
        }
    }
}

@MongoRepository
interface IndexedEntityRepository extends CrudRepository<IndexedEntity, String> {
}

@MappedEntity('indexed_entities')
class IndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'name_idx', unique = true)
    String name
}
