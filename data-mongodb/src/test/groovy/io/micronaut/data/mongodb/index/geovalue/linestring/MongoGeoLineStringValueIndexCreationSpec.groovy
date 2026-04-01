package io.micronaut.data.mongodb.index.geovalue.linestring

import com.mongodb.client.MongoClient
import com.mongodb.client.model.geojson.LineString
import com.mongodb.client.model.geojson.Position
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.model.DataType
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoGeoLineStringValueIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Shared
    GeoLineStringValueIndexedEntityRepository repository

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.geovalue.linestring']
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
        repository = applicationContext.getBean(GeoLineStringValueIndexedEntityRepository)
    }

    void 'creates geospatial index on a MongoDB LineString value'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'geo_linestring_value_indexed_entities')
            assert indexes*.name.contains('geo_linestring_location_idx')
            def index = indexes.find { it.name == 'geo_linestring_location_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'route'
            assert index.fields[0].kind() == '2dsphere'
        }
    }

    void 'persists and reads MongoDB LineString value'() {
        given:
        def lineString = new LineString([
                new Position(-73.99d, 40.75d),
                new Position(-73.98d, 40.74d),
                new Position(-73.97d, 40.73d)
        ])

        when:
        def saved = repository.save(new GeoLineStringValueIndexedEntity(route: lineString))
        def loaded = repository.findById(saved.id).orElseThrow()

        then:
        loaded.route.coordinates.size() == 3
        loaded.route.coordinates[0].values[0] == -73.99d
        loaded.route.coordinates[0].values[1] == 40.75d
        loaded.route.coordinates[2].values[0] == -73.97d
        loaded.route.coordinates[2].values[1] == 40.73d
    }
}

@MongoRepository
interface GeoLineStringValueIndexedEntityRepository extends CrudRepository<GeoLineStringValueIndexedEntity, String> {
}

@MappedEntity('geo_linestring_value_indexed_entities')
class GeoLineStringValueIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @TypeDef(type = DataType.OBJECT)
    @MongoGeoIndexed(name = 'geo_linestring_location_idx')
    LineString route
}
