package io.micronaut.data.mongodb.index.geo2d

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexType
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoGeo2dIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Shared
    Geo2dIndexedEntityRepository repository

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.geo2d']
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
        repository = applicationContext.getBean(Geo2dIndexedEntityRepository)
    }

    void 'creates field 2d index'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'geo2d_indexed_entities')
            assert indexes*.name.contains('geo2d_location_idx')
            def index = indexes.find { it.name == 'geo2d_location_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'location'
            assert index.fields[0].order() == null
            assert index.fields[0].kind() == '2d'
        }
    }

    void 'saves and loads map-backed 2d location via repository'() {
        given:
        def entity = new Geo2dIndexedEntity(location: [x: -73.99d, y: 40.75d])

        when:
        def saved = repository.save(entity)
        def loaded = repository.findById(saved.id).orElseThrow()

        then:
        loaded.location == [x: -73.99d, y: 40.75d]
    }
}

@MongoRepository
interface Geo2dIndexedEntityRepository extends CrudRepository<Geo2dIndexedEntity, String> {
}

@MappedEntity('geo2d_indexed_entities')
class Geo2dIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'geo2d_location_idx', type = MongoGeoIndexType.GEO_2D)
    Map<String, Object> location
}
