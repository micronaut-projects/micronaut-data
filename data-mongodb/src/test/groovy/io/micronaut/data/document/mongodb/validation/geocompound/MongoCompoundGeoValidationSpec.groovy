package io.micronaut.data.document.mongodb.validation.geocompound

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexType
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoCompoundGeoValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.geocompound']
    }

    void 'fails fast when compound geospatial field also declares numeric direction'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('cannot define a numeric direction')
    }
}

@MongoRepository
interface InvalidCompoundGeoEntityRepository extends CrudRepository<InvalidCompoundGeoEntity, String> {
}

@MongoCompoundIndex(
        name = 'invalid_geo_name_idx',
        fields = [
                @MongoCompoundIndexField(value = 'location', geo = true, geoType = MongoGeoIndexType.GEO_2DSPHERE, direction = MongoIndexDirection.DESC),
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('invalid_geo_compound_indexed_entities')
class InvalidCompoundGeoEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> location

    String name
}
