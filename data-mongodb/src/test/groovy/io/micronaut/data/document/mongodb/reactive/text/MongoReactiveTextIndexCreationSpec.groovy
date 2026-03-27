package io.micronaut.data.document.mongodb.reactive.text

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.reactive.MongoSelectReactiveDriver
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoTextIndexed
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoReactiveTextIndexCreationSpec extends Specification implements MongoSelectReactiveDriver {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.reactive.text']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates field text index in reactive mode'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(io.micronaut.data.mongodb.init.MongoReactiveCollectionsCreator)
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'reactive_text_indexed_entities')
            assert indexes*.name.contains('reactive_text_name_idx')
            def index = indexes.find { it.name == 'reactive_text_name_idx' }
            assert index.fields.size() == 2
            assert index.fields*.path().contains('_fts')
            assert index.fields*.path().contains('_ftsx')
        }
    }
}

@MongoRepository
interface ReactiveTextIndexedEntityRepository extends CrudRepository<ReactiveTextIndexedEntity, String> {
}

@MappedEntity('reactive_text_indexed_entities')
class ReactiveTextIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoTextIndexed(name = 'reactive_text_name_idx')
    String name
}
