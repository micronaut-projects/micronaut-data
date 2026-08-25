package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.IndexedBook;
import io.micronaut.data.repository.CrudRepository;
import org.dizitart.no2.spatial.GeoPoint;
import org.locationtech.jts.geom.Geometry;

import java.util.List;

/**
 * Repository for {@link IndexedBook} used in index creation tests.
 */
@NitriteRepository
public interface IndexedBookRepository extends CrudRepository<IndexedBook, String> {

    /**
     * Search books by description text (full-text search).
     *
     * @param text the search text
     * @return matching books
     */
    @Query("{\"description\": {\"$text\": :text}}")
    List<IndexedBook> searchByDescription(String text);

    /**
     * Find books near the given geometry using an explicit Nitrite spatial query.
     *
     * @param location the reference geometry
     * @param maxDistance the maximum distance from the reference geometry
     * @return matching books
     */
    @Query("{\"location\": {\"$near\": {\"center\": :location, \"distance\": :maxDistance}}}")
    List<IndexedBook> findByLocationNearQuery(Geometry location, double maxDistance);

    /**
     * Find books near the given geometry using a derived query.
     *
     * @param location the reference geometry
     * @param maxDistance the maximum distance from the reference geometry
     * @return matching books
     */
    List<IndexedBook> findByLocationNear(Geometry location, double maxDistance);

    /**
     * Find books near the given geographic point, with the distance always interpreted
     * geodesically (meters) regardless of latitude. A derived-query method can't take a
     * {@link GeoPoint} parameter directly here because {@link IndexedBook#getLocation()} is typed
     * as {@link Geometry}, so this goes through an explicit Nitrite {@code $near} query instead.
     *
     * @param point the reference geographic point
     * @param maxDistanceMeters the maximum distance from the reference point, in meters
     * @return matching books
     */
    @Query("{\"location\": {\"$near\": {\"center\": :point, \"distance\": :maxDistanceMeters}}}")
    List<IndexedBook> findByLocationNearGeoPoint(GeoPoint point, double maxDistanceMeters);

    @Query("{\"location\": {\"$within\": :area}}")
    List<IndexedBook> findByLocationWithin(Geometry area);

    @Query("{\"location\": {\"$intersects\": :geometry}}")
    List<IndexedBook> findByLocationIntersects(Geometry geometry);

    List<IndexedBook> findByLocationGeoWithin(Geometry area);

    List<IndexedBook> findByLocationGeoIntersects(Geometry geometry);

    List<IndexedBook> findByTitleAndPages(String title, int pages);
}
