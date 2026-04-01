package io.micronaut.data.document.mongodb.geovalue.geometrycollection

import com.mongodb.client.MongoClient
import com.mongodb.client.model.geojson.GeometryCollection
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.model.DataType
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
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
    }

    void 'creates geospatial index on a MongoDB GeometryCollection value'() {
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
}


@MappedEntity('geo_geometry_collection_value_indexed_entities')
class GeoGeometryCollectionValueIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @TypeDef(type = DataType.OBJECT)
    @MongoGeoIndexed(name = 'geo_geometry_collection_location_idx')
    GeometryCollection geometry
}
