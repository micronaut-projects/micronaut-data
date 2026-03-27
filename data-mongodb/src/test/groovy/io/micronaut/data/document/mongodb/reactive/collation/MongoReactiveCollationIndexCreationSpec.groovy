package io.micronaut.data.document.mongodb.reactive.collation

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.reactive.MongoSelectReactiveDriver
import io.micronaut.data.mongodb.annotation.MongoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.Document
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoReactiveCollationIndexCreationSpec extends Specification implements MongoSelectReactiveDriver {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.reactive.collation']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates field index with collation in reactive mode'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(io.micronaut.data.mongodb.init.MongoReactiveCollectionsCreator)
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'reactive_collation_indexed_entities')
            assert indexes*.name.contains('reactive_collation_name_idx')
            def index = indexes.find { it.name == 'reactive_collation_name_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'name'
            assert index.fields[0].order() == 1
            assert index.collation != null
            assert ((Document) index.collation).getString('locale') == 'en'
        }
    }
}

@MongoRepository
interface ReactiveCollationIndexedEntityRepository extends CrudRepository<ReactiveCollationIndexedEntity, String> {
}

@MappedEntity('reactive_collation_indexed_entities')
class ReactiveCollationIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'reactive_collation_name_idx', collation = '{ "locale": "en", "strength": 2 }')
    String name
}
