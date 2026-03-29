package io.micronaut.data.document.mongodb.reactive.compound.collectionpath

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.reactive.MongoSelectReactiveDriver
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoReactiveCompoundCollectionFieldIndexCreationSpec extends Specification implements MongoSelectReactiveDriver {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties() + [
            'micronaut.data.mongodb.create-collections': 'true',
            'micronaut.data.mongodb.create-indexes'    : 'true'
    ])

    @Shared
    MongoClient mongoClient = applicationContext.getBean(MongoClient)

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.reactive.compound.collectionpath']
    }

    void 'creates index for collection field (multikey) path in reactive mode'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(io.micronaut.data.mongodb.init.MongoReactiveCollectionsCreator)
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'reactive_compound_embedded_collection_path_entities')
            assert indexes*.name.contains('reactive_tags_idx')
            def index = indexes.find { it.name == 'reactive_tags_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'tags'
            assert index.fields[0].order() == 1
        }
    }
}

@MongoRepository
interface ReactiveCompoundCollectionFieldIndexedEntityRepository extends CrudRepository<ReactiveCompoundCollectionFieldIndexedEntity, String> {
}

@MongoCompoundIndex(
        name = 'reactive_tags_idx',
        fields = [
                @MongoCompoundIndexField(value = 'tags', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('reactive_compound_embedded_collection_path_entities')
class ReactiveCompoundCollectionFieldIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name

    List<String> tags
}
