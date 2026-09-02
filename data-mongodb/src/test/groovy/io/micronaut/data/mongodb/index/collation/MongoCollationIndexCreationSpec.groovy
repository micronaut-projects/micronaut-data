package io.micronaut.data.mongodb.index.collation

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoIndexed
import org.bson.Document
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoCollationIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.collation']
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

    void 'creates field index with collation'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'collation_indexed_entities')
            assert indexes*.name.contains('collation_name_idx')
            def index = indexes.find { it.name == 'collation_name_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'name'
            assert index.fields[0].order() == 1
            assert index.collation != null
            assert ((Document) index.collation).getString('locale') == 'en'
        }
    }

    void 'creates field index with extended collation through raw command path'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'extended_collation_indexed_entities')
            assert indexes*.name.contains('extended_collation_name_idx')
            def index = indexes.find { it.name == 'extended_collation_name_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'name'
            assert index.fields[0].order() == 1
            assert index.collation != null
            Document collation = (Document) index.collation
            assert collation.getString('locale') == 'en'
            assert collation.getInteger('strength') == 2
            assert collation.getBoolean('caseLevel')
            assert collation.getString('caseFirst') == 'upper'
            assert collation.getBoolean('numericOrdering')
            assert collation.getString('alternate') == 'shifted'
            assert collation.getString('maxVariable') == 'space'
            assert collation.getBoolean('normalization')
            assert collation.getBoolean('backwards')
        }
    }
}

@MappedEntity('collation_indexed_entities')
class CollationIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'collation_name_idx', collation = '{ "locale": "en", "strength": 2 }')
    String name
}

@MappedEntity('extended_collation_indexed_entities')
class ExtendedCollationIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(
            name = 'extended_collation_name_idx',
            comment = 'force-raw-command-path',
            collation = '{ "locale": "en", "strength": "secondary", "caseLevel": true, "caseFirst": "upper", "numericOrdering": true, "alternate": "shifted", "maxVariable": "space", "normalization": true, "backwards": true }'
    )
    String name
}
