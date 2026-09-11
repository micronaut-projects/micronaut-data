package io.micronaut.data.mongodb.index.validation.geobits

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexType
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoGeoBitsValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.geobits']
    }

    void 'fails fast when 2d bits is outside supported Mongo range'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Mongo 2d geospatial option 'bits'")
        e.message.contains('must be between 1 and 32 inclusive')
    }
}

@MongoRepository
interface InvalidGeoBitsEntityRepository extends CrudRepository<InvalidGeoBitsEntity, String> {
}

@MappedEntity('invalid_geo_bits_entities')
class InvalidGeoBitsEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'invalid_geo_bits_idx', type = MongoGeoIndexType.GEO_2D, bits = 33)
    Map<String, Object> location
}
