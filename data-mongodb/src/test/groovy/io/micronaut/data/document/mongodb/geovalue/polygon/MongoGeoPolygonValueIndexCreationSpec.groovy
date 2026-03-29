package io.micronaut.data.document.mongodb.geovalue.polygon

import com.mongodb.client.MongoClient
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
import io.micronaut.data.mongodb.geo.MongoGeoPoint
import io.micronaut.data.mongodb.geo.MongoGeoPolygon
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

    void 'creates geospatial index on a MongoGeoPolygon modeled value'() {
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

    void 'persists and reads MongoGeoPolygon modeled value'() {
        given:
        def polygon = new MongoGeoPolygon([
                [
                        new MongoGeoPoint(-73.99d, 40.75d),
                        new MongoGeoPoint(-73.98d, 40.75d),
                        new MongoGeoPoint(-73.98d, 40.74d),
                        new MongoGeoPoint(-73.99d, 40.74d),
                        new MongoGeoPoint(-73.99d, 40.75d)
                ]
        ])

        when:
        def saved = repository.save(new GeoPolygonValueIndexedEntity(area: polygon))
        def loaded = repository.findById(saved.id).orElseThrow()

        then:
        loaded.area.coordinates().size() == 1
        loaded.area.coordinates()[0].size() == 5
        loaded.area.coordinates()[0][0].x() == -73.99d
        loaded.area.coordinates()[0][0].y() == 40.75d
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
    MongoGeoPolygon area
}
