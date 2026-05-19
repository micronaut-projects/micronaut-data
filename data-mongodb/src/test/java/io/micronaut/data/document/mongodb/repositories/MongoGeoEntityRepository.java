package io.micronaut.data.document.mongodb.repositories;

import com.mongodb.client.model.geojson.LineString;
import com.mongodb.client.model.geojson.Point;
import com.mongodb.client.model.geojson.Polygon;
import io.micronaut.data.document.mongodb.entities.GeoEntity;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@MongoRepository
public interface MongoGeoEntityRepository extends CrudRepository<GeoEntity, String> {

    List<GeoEntity> findByPointGeoWithin(Polygon area);

    List<GeoEntity> findByPointGeoIntersects(Polygon area);

    List<GeoEntity> findByPointNear(Point center, double distance);
}
