package io.micronaut.data.document.mongodb.geovalue.implicit

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
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoImplicitCustomGeoPointValueIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Shared
    ImplicitCustomGeoPointValueIndexedEntityRepository repository

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.geovalue.implicit']
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
        repository = applicationContext.getBean(ImplicitCustomGeoPointValueIndexedEntityRepository)
    }

    void 'creates geospatial index on custom modeled value without explicit converter'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'implicit_custom_geo_point_value_indexed_entities')
            assert indexes*.name.contains('implicit_custom_geo_point_location_idx')
            def index = indexes.find { it.name == 'implicit_custom_geo_point_location_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'location'
            assert index.fields[0].kind() == '2dsphere'
        }
    }

    void 'persists and reads custom geospatial modeled value without explicit converter'() {
        when:
        def saved = repository.save(new ImplicitCustomGeoPointValueIndexedEntity(location: new ImplicitCustomGeoPoint(longitude: 12.5d, latitude: 45.8d)))
        def loaded = repository.findById(saved.id).orElseThrow()

        then:
        loaded.location.longitude == 12.5d
        loaded.location.latitude == 45.8d
    }
}

@MongoRepository
interface ImplicitCustomGeoPointValueIndexedEntityRepository extends CrudRepository<ImplicitCustomGeoPointValueIndexedEntity, String> {
}

@MappedEntity('implicit_custom_geo_point_value_indexed_entities')
class ImplicitCustomGeoPointValueIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @TypeDef(type = DataType.OBJECT)
    @MongoGeoIndexed(name = 'implicit_custom_geo_point_location_idx')
    ImplicitCustomGeoPoint location
}

@MappedEntity
class ImplicitCustomGeoPoint {
    double longitude
    double latitude
}
