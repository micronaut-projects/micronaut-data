package io.micronaut.data.document.mongodb.reactive.geo

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.reactive.MongoSelectReactiveDriver
import io.micronaut.data.mongodb.annotation.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoReactiveGeoIndexCreationSpec extends Specification implements MongoSelectReactiveDriver {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.reactive.geo']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates field 2dsphere index in reactive mode'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(io.micronaut.data.mongodb.init.MongoReactiveCollectionsCreator)
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'reactive_geo_indexed_entities')
            assert indexes*.name.contains('reactive_geo_location_idx')
            def index = indexes.find { it.name == 'reactive_geo_location_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'location'
            assert index.fields[0].order() == null
            assert index.fields[0].kind() == '2dsphere'
        }
    }
}

@MongoRepository
interface ReactiveGeoIndexedEntityRepository extends CrudRepository<ReactiveGeoIndexedEntity, String> {
}

@MappedEntity('reactive_geo_indexed_entities')
class ReactiveGeoIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'reactive_geo_location_idx')
    Map<String, Object> location
}
