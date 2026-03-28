package io.micronaut.data.document.mongodb.wildcard.toplevel.multiple

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoWildcardIndex
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoTopLevelWildcardMultipleDeclarationsIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.wildcard.toplevel.multiple']
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

    void 'merges equivalent multiple top-level wildcard declarations into a single managed index'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'top_level_wildcard_multiple_indexed_entities')
            def wildcardIndexes = indexes.findAll { it.fields.size() == 1 && it.fields[0].path() == '$**' }
            assert wildcardIndexes.size() == 1
            assert wildcardIndexes[0].name == 'top_level_wildcard_multiple_idx'
        }
    }
}

@MongoRepository
interface TopLevelWildcardMultipleIndexedEntityRepository extends CrudRepository<TopLevelWildcardMultipleIndexedEntity, String> {
}

@MongoWildcardIndex(name = 'top_level_wildcard_multiple_idx')
@MongoWildcardIndex(name = 'top_level_wildcard_multiple_idx')
@MappedEntity('top_level_wildcard_multiple_indexed_entities')
class TopLevelWildcardMultipleIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name
}
