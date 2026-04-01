package io.micronaut.data.document.mongodb.geovalue.polygon

import com.mongodb.client.MongoClient
import com.mongodb.client.model.geojson.Polygon
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

class MongoGeoPolygonValueIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Shared
    GeoPolygonValueIndexedEntityRepository repository

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.geovalue.polygon']
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
        repository = applicationContext.getBean(GeoPolygonValueIndexedEntityRepository)
    }

    void 'creates geospatial index on a MongoDB Polygon value'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'geo_polygon_value_indexed_entities')
            assert indexes*.name.contains('geo_polygon_location_idx')
            def index = indexes.find { it.name == 'geo_polygon_location_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'area'
            assert index.fields[0].kind() == '2dsphere'
        }
    }

    void 'persists and reads MongoDB Polygon value'() {
        given:
        def polygon = new Polygon([
                new Position(-73.99d, 40.75d),
                new Position(-73.98d, 40.75d),
                new Position(-73.98d, 40.74d),
                new Position(-73.99d, 40.74d),
                new Position(-73.99d, 40.75d)
        ])

        when:
        def saved = repository.save(new GeoPolygonValueIndexedEntity(area: polygon))
        def loaded = repository.findById(saved.id).orElseThrow()

        then:
        loaded.area.coordinates.exterior.size() == 5
        loaded.area.coordinates.exterior[0].values[0] == -73.99d
        loaded.area.coordinates.exterior[0].values[1] == 40.75d
    }
}

@MongoRepository
interface GeoPolygonValueIndexedEntityRepository extends CrudRepository<GeoPolygonValueIndexedEntity, String> {
}

@MappedEntity('geo_polygon_value_indexed_entities')
class GeoPolygonValueIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @TypeDef(type = DataType.OBJECT)
    @MongoGeoIndexed(name = 'geo_polygon_location_idx')
    Polygon area
}
