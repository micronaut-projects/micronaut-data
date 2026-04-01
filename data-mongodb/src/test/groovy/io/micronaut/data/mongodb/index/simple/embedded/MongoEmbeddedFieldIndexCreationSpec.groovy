package io.micronaut.data.mongodb.index.simple.embedded

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoIndexed
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoEmbeddedFieldIndexCreationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.simple.index.embedded']
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

    void 'creates field index declared inside embedded type'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'embedded_field_indexed_entities')
            assert indexes*.name.contains('embedded_state_idx')
            def index = indexes.find { it.name == 'embedded_state_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'location.state'
            assert index.fields[0].order() == 1
        }
    }
}

@MappedEntity('embedded_field_indexed_entities')
class EmbeddedFieldIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @Relation(Relation.Kind.EMBEDDED)
    EmbeddedLocation location
}

@Embeddable
class EmbeddedLocation {
    @MongoIndexed(name = 'embedded_state_idx')
    String state

    String city
}
