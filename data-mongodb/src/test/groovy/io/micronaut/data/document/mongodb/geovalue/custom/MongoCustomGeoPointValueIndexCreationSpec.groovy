package io.micronaut.data.document.mongodb.geovalue.custom

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.model.DataType
import io.micronaut.data.mongodb.annotation.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.geo.MongoGeoPointConverter
import io.micronaut.data.mongodb.geo.MongoGeoPointLike
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoCustomGeoPointValueIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.geovalue.custom']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates geospatial index on a custom point-like modeled value'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(io.micronaut.data.mongodb.init.MongoCollectionsCreator)
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'custom_geo_point_value_indexed_entities')
            assert indexes*.name.contains('custom_geo_point_location_idx')
            def index = indexes.find { it.name == 'custom_geo_point_location_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'location'
            assert index.fields[0].kind() == '2dsphere'
        }
    }
}

@MongoRepository
interface CustomGeoPointValueIndexedEntityRepository extends CrudRepository<CustomGeoPointValueIndexedEntity, String> {
}

@MappedEntity('custom_geo_point_value_indexed_entities')
class CustomGeoPointValueIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @TypeDef(type = DataType.OBJECT)
    @MappedProperty(converter = MongoGeoPointConverter, converterPersistedType = java.util.Map)
    @MongoGeoIndexed(name = 'custom_geo_point_location_idx')
    CustomGeoPoint location
}

@MappedEntity
class CustomGeoPoint implements MongoGeoPointLike {
    double x
    double y

    @Override
    double x() {
        return x
    }

    @Override
    double y() {
        return y
    }
}
