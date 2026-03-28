package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.IndexedBook;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;
import org.locationtech.jts.geom.Geometry;

/**
 * Repository for {@link IndexedBook} used in index creation tests.
 */
@NitriteRepository
public interface IndexedBookRepository extends CrudRepository<IndexedBook, String> {

    /**
     * Search books by description text (full-text search).
     * @param text the search text
     * @return matching books
     */
    @Query("{\"description\": {\"$text\": :text}}")
    List<IndexedBook> searchByDescription(String text);

    /**
     * Find books near a location using spatial $near filter.
     * @param location the reference point
     * @param maxDistance maximum distance in meters
     * @return books near the location
     */
    @Query("{\"location\": {\"$near\": {\"center\": :location, \"distance\": :maxDistance}}}")
    List<IndexedBook> findByLocationNear(Geometry location, double maxDistance);

    /**
     * Find books within a geometry using spatial $within filter.
     * @param area the search area (polygon or circle)
     * @return books within the area
     */
    @Query("{\"location\": {\"$within\": :area}}")
    List<IndexedBook> findByLocationWithin(Geometry area);

    /**
     * Find books intersecting a geometry using spatial $intersects filter.
     * @param geometry the geometry to check intersection with
     * @return books intersecting the geometry
     */
    @Query("{\"location\": {\"$intersects\": :geometry}}")
    List<IndexedBook> findByLocationIntersects(Geometry geometry);

    /**
     * Find books by title and pages combination to test compound index.
     * @param title the book title
     * @param pages the number of pages
     * @return books matching both criteria
     */
    List<IndexedBook> findByTitleAndPages(String title, int pages);
}
