package io.micronaut.data.document.mongodb.validation.georules

import com.mongodb.client.model.geojson.Point
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexType
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoGeoIndexValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.georules']
    }

    void 'fails fast when 2d-specific options are used on a 2dsphere index'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('2d-specific geospatial options are only supported for Mongo 2d indexes')
    }
}

@MongoRepository
interface InvalidGeoIndexedEntityRepository extends CrudRepository<InvalidGeoIndexedEntity, String> {
}

@MappedEntity('invalid_geo_indexed_entities')
class InvalidGeoIndexedEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'invalid_geo_idx', type = MongoGeoIndexType.GEO_2DSPHERE, bits = 26)
    Point location
}
