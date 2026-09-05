package example;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

/**
 * Repository for {@link IndexedBook} demonstrating full-text and spatial queries.
 */
@NitriteRepository
public interface IndexedBookRepository extends CrudRepository<IndexedBook, String> {

    /**
     * Search books by description text (full-text search).
     * @param text the search text
     * @return matching books
     */
    // tag::text-query[]
    @Query("""
    {"description": {"$text": :text}}
    """)
    List<IndexedBook> searchByDescription(String text);
    // end::text-query[]

    /**
     * Find books near a location using spatial $near filter.
     * @param location the reference point
     * @param maxDistance maximum distance in meters
     * @return books near the location
    */
    // tag::near-query[]
    @Query("""
    {"location": {"$near": {"center": :location, "distance": :maxDistance}}}
    """)
    List<IndexedBook> findByLocationNearQuery(Geometry location, double maxDistance);
    // end::near-query[]

    // tag::derived-spatial[]
    List<IndexedBook> findByLocationNear(Geometry location, double maxDistance);

    /**
     * Find books within a geometry using spatial $within filter.
     * @param area the search area
     * @return books within the area
     */
    // tag::within-query[]
    @Query("""
    {"location": {"$within": :area}}
    """)
    List<IndexedBook> findByLocationWithin(Polygon area);
    // end::within-query[]

    List<IndexedBook> findByLocationGeoWithin(Geometry area);

    /**
     * Find books intersecting a geometry using spatial $intersects filter.
     * @param geometry the geometry to check intersection with
     * @return books intersecting the geometry
     */
    // tag::intersects-query[]
    @Query("""
    {"location": {"$intersects": :geometry}}
    """)
    List<IndexedBook> findByLocationIntersects(LineString geometry);
    // end::intersects-query[]

    List<IndexedBook> findByLocationGeoIntersects(Geometry geometry);
    // end::derived-spatial[]
}
