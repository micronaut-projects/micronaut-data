package io.micronaut.data.mongodb.index.wildcard.toplevel

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoWildcardIndex
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoTopLevelWildcardIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.wildcard.toplevel']
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

    void 'creates top-level wildcard index'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'top_level_wildcard_indexed_entities')
            assert indexes*.name.contains('top_level_wildcard_idx')
            def index = indexes.find { it.name == 'top_level_wildcard_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == '$**'
            assert index.fields[0].order() == 1
        }
    }
}

@MongoWildcardIndex(name = 'top_level_wildcard_idx')
@MappedEntity('top_level_wildcard_indexed_entities')
class TopLevelWildcardIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name

    Map<String, Object> metadata
}
