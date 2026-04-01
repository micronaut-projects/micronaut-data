package io.micronaut.data.mongodb.index.compound.dotpath

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Relation
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoCompoundEmbeddedPathIndexCreationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.compound.dotpath']
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

    void 'creates index for valid embedded dot-notation path'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'compound_embedded_path_indexed_entities')
            assert indexes*.name.contains('location_state_idx')
            def index = indexes.find { it.name == 'location_state_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'location.state'
            assert index.fields[0].order() == 1
        }
    }
}

@MongoCompoundIndex(
        name = 'location_state_idx',
        fields = [
                @MongoCompoundIndexField(value = 'location.state', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('compound_embedded_path_indexed_entities')
class CompoundEmbeddedPathIndexedEntity {
    @Id
    @GeneratedValue
    String id

    String name

    @Relation(Relation.Kind.EMBEDDED)
    EmbeddedLocation location
}

@Embeddable
class EmbeddedLocation {
    String state
    String city
}
