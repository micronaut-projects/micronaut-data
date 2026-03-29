package io.micronaut.data.document.mongodb.validation.indexbootstrap

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoIndexBootstrapWithoutCollectionsSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.indexbootstrap']
    }

    Class<?> expectedCollectionsCreatorBeanType() {
        io.micronaut.data.mongodb.init.MongoCollectionsCreator
    }

    void 'creates indexes for existing collections when create collections is disabled'() {
        given:
        ApplicationContext collectionBootstrapContext
        ApplicationContext indexBootstrapContext
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        when: 'the collection is created first without index creation'
        collectionBootstrapContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'false'
        ])

        then:
        noExceptionThrown()
        MongoClient collectionBootstrapClient = collectionBootstrapContext.getBean(MongoClient)
        conditions.eventually {
            assert collectionBootstrapClient.getDatabase('test').listCollectionNames().into([]).contains('index_bootstrap_entities')
        }

        when: 'index bootstrap runs with collection creation disabled'
        indexBootstrapContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'false',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        indexBootstrapContext.containsBean(expectedCollectionsCreatorBeanType())
        MongoClient indexBootstrapClient = indexBootstrapContext.getBean(MongoClient)
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(indexBootstrapClient, 'test', 'index_bootstrap_entities')
            assert indexes*.name.contains('index_bootstrap_name_idx')
            def index = indexes.find { it.name == 'index_bootstrap_name_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'name'
            assert index.fields[0].order() == 1
        }

        cleanup:
        collectionBootstrapContext?.close()
        indexBootstrapContext?.close()
    }
}

@MongoRepository
interface IndexBootstrapEntityRepository extends CrudRepository<IndexBootstrapEntity, String> {
}

@MongoCompoundIndex(
        name = 'index_bootstrap_name_idx',
        fields = [
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('index_bootstrap_entities')
class IndexBootstrapEntity {
    @Id
    @GeneratedValue
    String id

    String name
}
