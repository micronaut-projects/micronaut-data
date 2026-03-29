package io.micronaut.data.document.mongodb.geocompound.options

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexType
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class MongoCompoundGeo2dOptionsIndexCreationSpec extends Specification implements MongoTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.geocompound.options']
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

    void 'creates compound 2d index with bits min and max on geospatial field'() {
        given:
        def conditions = new PollingConditions(timeout: 10, delay: 0.25)

        expect:
        applicationContext.containsBean(expectedCollectionsCreatorBeanType())
        conditions.eventually {
            def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'test', 'geo2d_compound_options_indexed_entities')
            assert indexes*.name.contains('geo2d_name_idx')
            def index = indexes.find { it.name == 'geo2d_name_idx' }
            assert index.fields.size() == 2
            assert index.fields[0].path() == 'location'
            assert index.fields[0].kind() == '2d'
            assert index.fields[1].path() == 'name'
            assert index.fields[1].order() == 1
            assert index.min == -180
            assert index.max == 180
        }
    }
}

@MongoRepository
interface Geo2dCompoundOptionsEntityRepository extends CrudRepository<Geo2dCompoundOptionsEntity, String> {
}

@MongoCompoundIndex(
        name = 'geo2d_name_idx',
        fields = [
                @MongoCompoundIndexField(value = 'location', geo = true, geoType = MongoGeoIndexType.GEO_2D, bits = 26, min = -180, max = 180),
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('geo2d_compound_options_indexed_entities')
class Geo2dCompoundOptionsEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> location

    String name
}
