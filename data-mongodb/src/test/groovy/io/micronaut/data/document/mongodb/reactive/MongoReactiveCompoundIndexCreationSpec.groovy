package io.micronaut.data.document.mongodb.reactive

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.mongodb.annotation.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.MongoIndexDirection
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoReactiveCompoundIndexCreationSpec extends Specification implements MongoSelectReactiveDriver {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.reactive']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates declared compound indexes for existing collections with reactive driver selected'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(io.micronaut.data.mongodb.init.MongoReactiveCollectionsCreator)
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'reactive_compound_indexed_entities')
            assert indexes*.name.contains('reactive_name_age_idx')
            def index = indexes.find { it.name == 'reactive_name_age_idx' }
            assert index.unique
            assert index.fields.size() == 2
            assert index.fields[0].path() == 'name'
            assert index.fields[0].order() == 1
            assert index.fields[1].path() == 'age'
            assert index.fields[1].order() == -1
        }
    }
}

@MongoRepository
interface ReactiveCompoundIndexedEntityRepository extends CrudRepository<ReactiveCompoundIndexedEntity, String> {
}

@MongoCompoundIndex(
        name = 'reactive_name_age_idx',
        unique = true,
        fields = [
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC),
                @MongoCompoundIndexField(value = 'age', direction = MongoIndexDirection.DESC)
        ]
)
@MappedEntity('reactive_compound_indexed_entities')
class ReactiveCompoundIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name

    Integer age
}
