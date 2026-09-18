package io.micronaut.data.mongodb.index.validation.geocompoundsphereversion

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexType
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.repository.CrudRepository

@MongoRepository
interface InvalidCompoundGeoSphereVersionEntityRepository extends CrudRepository<InvalidCompoundGeoSphereVersionEntity, String> {
}

@MongoCompoundIndex(
        name = 'invalid_compound_geo_sphere_version_idx',
        fields = [
                @MongoCompoundIndexField(value = 'location', geo = true, geoType = MongoGeoIndexType.GEO_2D, sphereVersion = 3),
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC)
        ]
)
@MappedEntity('invalid_compound_geo_sphere_version_entities')
class InvalidCompoundGeoSphereVersionEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> location

    String name
}
