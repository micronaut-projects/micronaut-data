package io.micronaut.data.document.mongodb.validation.geocompoundopts

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.MongoIndexDirection
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoCompoundGeoOptionsValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.geocompoundopts']
    }

    void 'fails fast when 2d options are used on non-geospatial compound field'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('require geo=true')
    }
}

@MongoRepository
interface InvalidGeoOptionsEntityRepository extends CrudRepository<InvalidGeoOptionsEntity, String> {
}

@MongoCompoundIndex(
        name = 'invalid_geo_options_idx',
        fields = [
                @MongoCompoundIndexField(value = 'location', bits = 26),
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('invalid_geo_options_indexed_entities')
class InvalidGeoOptionsEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> location

    String name
}
