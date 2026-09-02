package io.micronaut.data.mongodb.index.validation.geosphereversion

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

class MongoGeoSphereVersionValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.geosphereversion']
    }

    void 'fails fast when sphereVersion is used on non-2dsphere geospatial index'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('2dsphere-specific geospatial options are only supported for Mongo 2dsphere indexes')
    }
}

@MongoRepository
interface InvalidGeoSphereVersionEntityRepository extends CrudRepository<InvalidGeoSphereVersionEntity, String> {
}

@MappedEntity('invalid_geo_sphere_version_entities')
class InvalidGeoSphereVersionEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'invalid_geo_sphere_version_idx', type = MongoGeoIndexType.GEO_2D, sphereVersion = 3)
    Map<String, Object> location
}
