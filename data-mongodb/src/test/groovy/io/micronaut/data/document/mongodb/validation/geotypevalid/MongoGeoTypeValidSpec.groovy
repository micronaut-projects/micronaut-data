package io.micronaut.data.document.mongodb.validation.geotypevalid

import com.mongodb.client.model.geojson.Point
import com.mongodb.client.model.geojson.Position
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoGeoTypeValidSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.geotypevalid']
    }

    void 'starts when MongoGeoIndexed uses supported MongoDB GeoJSON type'() {
        when:
        def context = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        noExceptionThrown()

        cleanup:
        context?.close()
    }
}

@MongoRepository
interface ValidGeoTypeEntityRepository extends CrudRepository<ValidGeoTypeEntity, String> {
}

@MappedEntity('valid_geo_type_entities')
class ValidGeoTypeEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'valid_geo_type_idx')
    Point location = new Point(new Position(-73.99d, 40.75d))
}
