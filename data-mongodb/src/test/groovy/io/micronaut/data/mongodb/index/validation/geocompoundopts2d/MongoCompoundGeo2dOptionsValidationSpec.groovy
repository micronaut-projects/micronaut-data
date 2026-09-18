package io.micronaut.data.mongodb.index.validation.geocompoundopts2d

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import spock.lang.Specification

class MongoCompoundGeo2dOptionsValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.geocompoundopts2d']
    }

    void 'fails fast when 2d-specific options are used without geo=true on a compound field'() {
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
interface InvalidCompoundGeo2dOptionsEntityRepository extends CrudRepository<InvalidCompoundGeo2dOptionsEntity, String> {
}

@MongoCompoundIndex(
        name = 'invalid_geo2d_opts_idx',
        fields = [
                @MongoCompoundIndexField(value = 'location', bits = 26),
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('invalid_geo2d_opts_entities')
class InvalidCompoundGeo2dOptionsEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> location

    String name
}
