package io.micronaut.data.document.mongodb.geovalue.rawquery;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.mongodb.annotation.MongoFindQuery;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.geo.MongoGeoMultiPoint;
import io.micronaut.data.mongodb.geo.MongoGeoPoint;
import io.micronaut.data.repository.CrudRepository;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@MongoRepository
public interface MongoGeoRawQueryEntityRepository extends CrudRepository<MongoGeoRawQueryEntity, String> {

    @MongoFindQuery(filter = "{'locations': {'$eq': :locations}}")
    Optional<MongoGeoRawQueryEntity> findByLocationsRaw(MongoGeoMultiPoint locations);

    @MongoFindQuery(filter = "{'locations': {'$eq': :locations}}")
    Optional<MongoGeoRawQueryEntity> findByLocationsRawNullable(@Nullable MongoGeoMultiPoint locations);

    @MongoFindQuery(filter = "{'locations': {'$geoIntersects': {'$geometry': :geometry}}}")
    Optional<MongoGeoRawQueryEntity> findByIntersectsGeometry(MongoGeoPoint geometry);

    @MongoFindQuery(filter = "{'locations': {'$geoIntersects': {'$geometry': :geometry}}}")
    Optional<MongoGeoRawQueryEntity> findByIntersectsGeometryObject(Object geometry);

    @MongoFindQuery(filter = "{'locations': {'$geoIntersects': {'$geometry': :geometry}}}")
    Optional<MongoGeoRawQueryEntity> findByIntersectsGeometryWithExplicitConverter(
        @TypeDef(type = DataType.OBJECT, converter = ShiftedGeoPointConverter.class) MongoGeoPoint geometry
    );
}
