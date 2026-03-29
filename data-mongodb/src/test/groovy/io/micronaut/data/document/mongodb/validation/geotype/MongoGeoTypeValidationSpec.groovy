package io.micronaut.data.document.mongodb.validation.geotype

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoGeoTypeValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.geotype']
    }

    void 'fails fast when MongoGeoIndexed uses unsupported property type'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('requires a supported type')
    }
}

@MongoRepository
interface InvalidGeoTypeEntityRepository extends CrudRepository<InvalidGeoTypeEntity, String> {
}

@MappedEntity('invalid_geo_type_entities')
class InvalidGeoTypeEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'invalid_geo_type_idx')
    String location
}
