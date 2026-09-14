package io.micronaut.data.mongodb.index.compound.text

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
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoCompoundTextIndexCreationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.compound.text']
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

    void 'creates declared compound text index'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'compound_text_indexed_entities')
            assert indexes*.name.contains('tenant_search_idx')
            def index = indexes.find { it.name == 'tenant_search_idx' }
            assert index.fields[0].path() == 'tenant_id'
            assert index.fields[0].order() == 1
            assert index.fields*.path().contains('_fts')
            assert index.fields*.path().contains('_ftsx')
            assert index.fields[-1].path() == 'created_at'
            assert index.fields[-1].order() == -1
            assert index.weights.title == 2
            assert index.weights.description == 5
            assert index.defaultLanguage == 'english'
            assert index.languageOverride == 'docLang'
            assert index.textIndexVersion == 3
        }
    }
}

@MongoCompoundIndex(
        name = 'tenant_search_idx',
        defaultLanguage = 'english',
        languageOverride = 'docLang',
        textIndexVersion = 3,
        fields = [
                @MongoCompoundIndexField(value = 'tenantId', direction = MongoIndexDirection.ASC),
                @MongoCompoundIndexField(value = 'title', text = true, weight = 2),
                @MongoCompoundIndexField(value = 'description', text = true, weight = 5),
                @MongoCompoundIndexField(value = 'createdAt', direction = MongoIndexDirection.DESC)
        ]
)
@MappedEntity('compound_text_indexed_entities')
class CompoundTextIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String tenantId

    String title

    String description

    Long createdAt
}
