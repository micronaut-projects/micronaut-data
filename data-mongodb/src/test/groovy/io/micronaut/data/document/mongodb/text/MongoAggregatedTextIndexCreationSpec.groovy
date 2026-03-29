package io.micronaut.data.document.mongodb.text

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoTextIndexed
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoAggregatedTextIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.text']
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

    void 'creates a single aggregated text index from multiple text-indexed fields'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'aggregated_text_indexed_entities')
            assert indexes*.name.contains('aggregated_text_idx')
            def index = indexes.find { it.name == 'aggregated_text_idx' }
            assert index.fields.size() == 2
            assert index.fields*.path().contains('_fts')
            assert index.fields*.path().contains('_ftsx')
            assert index.defaultLanguage == 'french'
            assert index.languageOverride == 'lang'
            assert index.textIndexVersion == 3
        }
    }
}

@MongoRepository
interface AggregatedTextIndexedEntityRepository extends CrudRepository<AggregatedTextIndexedEntity, String> {
}

@MappedEntity('aggregated_text_indexed_entities')
class AggregatedTextIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoTextIndexed(name = 'aggregated_text_idx', weight = 2, defaultLanguage = 'french', languageOverride = 'lang', textIndexVersion = 3)
    String title

    @MongoTextIndexed(name = 'aggregated_text_idx', weight = 5, defaultLanguage = 'french', languageOverride = 'lang', textIndexVersion = 3)
    String description
}
