package io.micronaut.data.mongodb.index.hidden

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.mongodb.annotation.index.MongoIndexed
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoHiddenIndexCreationSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.hidden']
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

    void 'creates hidden single-field index'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'hidden_indexed_entities')
            assert indexes*.name.contains('hidden_name_idx')
            def index = indexes.find { it.name == 'hidden_name_idx' }
            assert index.hidden
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'name'
            assert index.fields[0].order() == 1
        }
    }

    void 'creates hidden compound index'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'hidden_compound_indexed_entities')
            assert indexes*.name.contains('hidden_compound_idx')
            def index = indexes.find { it.name == 'hidden_compound_idx' }
            assert index.hidden
            assert index.fields.size() == 2
            assert index.fields[0].path() == 'first_name'
            assert index.fields[0].order() == 1
            assert index.fields[1].path() == 'last_name'
            assert index.fields[1].order() == -1
        }
    }
}

@MappedEntity('hidden_indexed_entities')
class HiddenIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'hidden_name_idx', hidden = true)
    String name
}

@MongoCompoundIndex(
        name = 'hidden_compound_idx',
        hidden = true,
        fields = [
                @MongoCompoundIndexField('firstName'),
                @MongoCompoundIndexField(value = 'lastName', direction = MongoIndexDirection.DESC)
        ]
)
@MappedEntity('hidden_compound_indexed_entities')
class HiddenCompoundIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String firstName

    String lastName
}
