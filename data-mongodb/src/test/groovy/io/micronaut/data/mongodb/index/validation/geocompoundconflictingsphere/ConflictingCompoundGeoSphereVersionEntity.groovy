package io.micronaut.data.mongodb.index.validation.geocompoundconflictingsphere

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexType
import io.micronaut.data.repository.CrudRepository

@MongoRepository
interface ConflictingCompoundGeoSphereVersionEntityRepository extends CrudRepository<ConflictingCompoundGeoSphereVersionEntity, String> {
}

@MongoCompoundIndex(
        name = 'conflicting_compound_geo_sphere_version_idx',
        fields = [
                @MongoCompoundIndexField(value = 'startLocation', geo = true, geoType = MongoGeoIndexType.GEO_2DSPHERE, sphereVersion = 2),
                @MongoCompoundIndexField(value = 'endLocation', geo = true, geoType = MongoGeoIndexType.GEO_2DSPHERE, sphereVersion = 3)
        ]
)
@MappedEntity('conflicting_compound_geo_sphere_version_entities')
class ConflictingCompoundGeoSphereVersionEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> startLocation

    Map<String, Object> endLocation
}
