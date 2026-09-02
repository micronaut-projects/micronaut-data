package io.micronaut.data.mongodb.index.geovalue.multipoint

import com.mongodb.client.MongoClient
import com.mongodb.client.model.geojson.MultiPoint
import com.mongodb.client.model.geojson.Position
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoGeoMultiPointValueIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Shared
    GeoMultiPointValueIndexedEntityRepository repository

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.geovalue.multipoint']
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
        repository = applicationContext.getBean(GeoMultiPointValueIndexedEntityRepository)
    }

    void 'creates geospatial index on a MongoDB MultiPoint value'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'geo_multipoint_value_indexed_entities')
            assert indexes*.name.contains('geo_multipoint_location_idx')
            def index = indexes.find { it.name == 'geo_multipoint_location_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'locations'
            assert index.fields[0].kind() == '2dsphere'
        }
    }

    void 'persists and reads MongoDB MultiPoint value'() {
        given:
        def multiPoint = new MultiPoint([
                new Position(-73.99d, 40.75d),
                new Position(-73.98d, 40.74d),
                new Position(-73.97d, 40.73d)
        ])

        when:
        def saved = repository.save(new GeoMultiPointValueIndexedEntity(locations: multiPoint))
        def loaded = repository.findById(saved.id).orElseThrow()

        then:
        loaded.locations.coordinates.size() == 3
        loaded.locations.coordinates[0].values[0] == -73.99d
        loaded.locations.coordinates[0].values[1] == 40.75d
        loaded.locations.coordinates[2].values[0] == -73.97d
        loaded.locations.coordinates[2].values[1] == 40.73d
    }
}

@MongoRepository
interface GeoMultiPointValueIndexedEntityRepository extends CrudRepository<GeoMultiPointValueIndexedEntity, String> {
}

@MappedEntity('geo_multipoint_value_indexed_entities')
class GeoMultiPointValueIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'geo_multipoint_location_idx')
    MultiPoint locations
}
