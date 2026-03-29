package io.micronaut.data.document.mongodb.geovalue.multipolygon

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
import io.micronaut.data.mongodb.geo.MongoGeoMultiPolygon
import io.micronaut.data.mongodb.geo.MongoGeoPoint
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoGeoMultiPolygonValueIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Shared
    GeoMultiPolygonValueIndexedEntityRepository repository

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.geovalue.multipolygon']
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
        repository = applicationContext.getBean(GeoMultiPolygonValueIndexedEntityRepository)
    }

    void 'creates geospatial index on a MongoGeoMultiPolygon modeled value'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'geo_multipolygon_value_indexed_entities')
            assert indexes*.name.contains('geo_multipolygon_location_idx')
            def index = indexes.find { it.name == 'geo_multipolygon_location_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'areas'
            assert index.fields[0].kind() == '2dsphere'
        }
    }

    void 'persists and reads MongoGeoMultiPolygon modeled value'() {
        given:
        def multiPolygon = new MongoGeoMultiPolygon([
                [
                        [
                                new MongoGeoPoint(-73.99d, 40.75d),
                                new MongoGeoPoint(-73.98d, 40.75d),
                                new MongoGeoPoint(-73.98d, 40.74d),
                                new MongoGeoPoint(-73.99d, 40.74d),
                                new MongoGeoPoint(-73.99d, 40.75d)
                        ]
                ],
                [
                        [
                                new MongoGeoPoint(-74.01d, 40.73d),
                                new MongoGeoPoint(-74.00d, 40.73d),
                                new MongoGeoPoint(-74.00d, 40.72d),
                                new MongoGeoPoint(-74.01d, 40.72d),
                                new MongoGeoPoint(-74.01d, 40.73d)
                        ]
                ]
        ])

        when:
        def saved = repository.save(new GeoMultiPolygonValueIndexedEntity(areas: multiPolygon))
        def loaded = repository.findById(saved.id).orElseThrow()

        then:
        loaded.areas.coordinates().size() == 2
        loaded.areas.coordinates()[0].size() == 1
        loaded.areas.coordinates()[0][0].size() == 5
        loaded.areas.coordinates()[0][0][0].x() == -73.99d
        loaded.areas.coordinates()[0][0][0].y() == 40.75d
        loaded.areas.coordinates()[1][0][0].x() == -74.01d
        loaded.areas.coordinates()[1][0][0].y() == 40.73d
    }
}

@MongoRepository
interface GeoMultiPolygonValueIndexedEntityRepository extends CrudRepository<GeoMultiPolygonValueIndexedEntity, String> {
}

@MappedEntity('geo_multipolygon_value_indexed_entities')
class GeoMultiPolygonValueIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @TypeDef(type = DataType.OBJECT)
    @MongoGeoIndexed(name = 'geo_multipolygon_location_idx')
    MongoGeoMultiPolygon areas
}
