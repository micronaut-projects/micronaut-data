package io.micronaut.data.document.mongodb.compound

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoCompoundIndexCreationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.compound']
    }

    Class<?> expectedCollectionsCreatorBeanType() {
        io.micronaut.data.mongodb.init.MongoCollectionsCreator
    }
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties() + [
            'micronaut.data.mongodb.create-collections': 'true',
            'micronaut.data.mongodb.create-indexes'    : 'true'
    ])

    @Shared
    MongoClient mongoClient = applicationContext.getBean(MongoClient)

    def setupSpec() {
       // mongoClient.getDatabase('test').getCollection('compound_indexed_entities').drop()
       // mongoClient.getDatabase('test').createCollection('compound_indexed_entities')
    }

    void 'creates declared compound indexes for existing collections'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'compound_indexed_entities')
            assert indexes*.name.contains('name_age_idx')
            def index = indexes.find { it.name == 'name_age_idx' }
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
interface CompoundIndexedEntityRepository extends CrudRepository<CompoundIndexedEntity, String> {
}

@MongoCompoundIndex(
        name = 'name_age_idx',
        unique = true,
        fields = [
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC),
                @MongoCompoundIndexField(value = 'age', direction = MongoIndexDirection.DESC)
        ]
)
@MappedEntity('compound_indexed_entities')
class CompoundIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name

    Integer age
}
