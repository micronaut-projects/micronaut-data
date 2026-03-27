package io.micronaut.data.document.mongodb.geo2d.options

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.MongoGeoIndexType
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoGeo2dOptionsIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.geo2d.options']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates 2d index with bits min and max'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(io.micronaut.data.mongodb.init.MongoCollectionsCreator)
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'geo2d_options_indexed_entities')
            assert indexes*.name.contains('geo2d_options_idx')
            def index = indexes.find { it.name == 'geo2d_options_idx' }
            assert index.fields.size() == 1
            assert index.fields[0].path() == 'location'
            assert index.fields[0].kind() == '2d'
        }
    }
}

@MongoRepository
interface Geo2dOptionsIndexedEntityRepository extends CrudRepository<Geo2dOptionsIndexedEntity, String> {
}

@MappedEntity('geo2d_options_indexed_entities')
class Geo2dOptionsIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'geo2d_options_idx', type = MongoGeoIndexType.GEO_2D, bits = 26, min = -180, max = 180)
    Map<String, Object> location
}
