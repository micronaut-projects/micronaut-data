package io.micronaut.data.document.mongodb.text.embedded

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoTextIndexed
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoEmbeddedTextIndexCreationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.text.embedded']
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

    void 'creates text index declared inside embedded type'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'embedded_text_indexed_entities')
            assert indexes*.name.contains('embedded_text_idx')
            def index = indexes.find { it.name == 'embedded_text_idx' }
            assert index.fields.size() == 2
            assert index.fields*.path().contains('_fts')
            assert index.fields*.path().contains('_ftsx')
        }
    }
}

@MongoRepository
interface EmbeddedTextIndexedEntityRepository extends CrudRepository<EmbeddedTextIndexedEntity, String> {
}

@MappedEntity('embedded_text_indexed_entities')
class EmbeddedTextIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @Relation(Relation.Kind.EMBEDDED)
    EmbeddedTextDetails details
}

@Embeddable
class EmbeddedTextDetails {
    String title

    @MongoTextIndexed(name = 'embedded_text_idx', weight = 3)
    String city
}
