package example;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.repository.CrudRepository;
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
    @Query("{\"description\": {\"$text\": :text}}")
    List<IndexedBook> searchByDescription(String text);
    // end::text-query[]

    /**
     * Find books near a location using spatial $near filter.
     * @param location the reference point (as GeoJSON or WKT)
     * @param maxDistance maximum distance in meters
     * @return books near the location
     */
    // tag::near-query[]
    @Query("{\"location\": {\"$near\": {\"center\": :location, \"distance\": :maxDistance}}}")
    List<IndexedBook> findByLocationNear(String location, double maxDistance);
    // end::near-query[]

    /**
     * Find books within a geometry using spatial $within filter.
     * @param area the search area (polygon or circle as GeoJSON or WKT)
     * @return books within the area
     */
    // tag::within-query[]
    @Query("{\"location\": {\"$within\": :area}}")
    List<IndexedBook> findByLocationWithin(String area);
    // end::within-query[]

    /**
     * Find books intersecting a geometry using spatial $intersects filter.
     * @param geometry the geometry to check intersection with (as GeoJSON or WKT)
     * @return books intersecting the geometry
     */
    // tag::intersects-query[]
    @Query("{\"location\": {\"$intersects\": :geometry}}")
    List<IndexedBook> findByLocationIntersects(String geometry);
    // end::intersects-query[]
}
