package io.micronaut.data.document.mongodb.geovalue.geometrycollection

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
import io.micronaut.data.mongodb.geo.MongoGeoGeometryCollection
import io.micronaut.data.mongodb.geo.MongoGeoLineString
import io.micronaut.data.mongodb.geo.MongoGeoMultiPoint
import io.micronaut.data.mongodb.geo.MongoGeoPoint
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoGeoGeometryCollectionValueIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Shared
    GeoGeometryCollectionValueIndexedEntityRepository repository

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.geovalue.geometrycollection']
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
        repository = applicationContext.getBean(GeoGeometryCollectionValueIndexedEntityRepository)
    }

    void 'creates geospatial index on a MongoGeoGeometryCollection modeled value'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'geo_geometry_collection_value_indexed_entities')
            assert indexes*.name.contains('geo_geometry_collection_location_idx')
            def index = indexes.find { it.name == 'geo_geometry_collection_location_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'geometry'
            assert index.fields[0].kind() == '2dsphere'
        }
    }

    void 'persists and reads MongoGeoGeometryCollection modeled value'() {
        given:
        def geometryCollection = new MongoGeoGeometryCollection([
                new MongoGeoPoint(-73.99d, 40.75d),
                new MongoGeoMultiPoint([
                        new MongoGeoPoint(-73.98d, 40.74d),
                        new MongoGeoPoint(-73.97d, 40.73d)
                ]),
                new MongoGeoLineString([
                        new MongoGeoPoint(-74.01d, 40.72d),
                        new MongoGeoPoint(-74.00d, 40.71d)
                ])
        ])

        when:
        def saved = repository.save(new GeoGeometryCollectionValueIndexedEntity(geometry: geometryCollection))
        def loaded = repository.findById(saved.id).orElseThrow()

        then:
        loaded.geometry.geometries().size() == 3
        loaded.geometry.geometries()[0] instanceof MongoGeoPoint
        loaded.geometry.geometries()[1] instanceof MongoGeoMultiPoint
        loaded.geometry.geometries()[2] instanceof MongoGeoLineString
    }
}

@MongoRepository
interface GeoGeometryCollectionValueIndexedEntityRepository extends CrudRepository<GeoGeometryCollectionValueIndexedEntity, String> {
}

@MappedEntity('geo_geometry_collection_value_indexed_entities')
class GeoGeometryCollectionValueIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @TypeDef(type = DataType.OBJECT)
    @MongoGeoIndexed(name = 'geo_geometry_collection_location_idx')
    MongoGeoGeometryCollection geometry
}
