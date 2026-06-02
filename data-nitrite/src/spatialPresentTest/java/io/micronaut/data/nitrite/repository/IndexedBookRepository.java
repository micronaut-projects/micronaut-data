package io.micronaut.data.nitrite.repository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.IndexedBook;
import io.micronaut.data.repository.CrudRepository;
import org.locationtech.jts.geom.Geometry;

import java.util.List;

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
     * Search books by description text (full-text search).
     */
    @Query("{\"location\": {\"$near\": {\"center\": :location, \"distance\": :maxDistance}}}")
    List<IndexedBook> findByLocationNearQuery(Geometry location, double maxDistance);

    /**
     * Find books within a geometry (derived query — no @Query).
     */
    List<IndexedBook> findByLocationNear(Geometry location, double maxDistance);

    @Query("{\"location\": {\"$within\": :area}}")
    List<IndexedBook> findByLocationWithin(Geometry area);

    @Query("{\"location\": {\"$intersects\": :geometry}}")
    List<IndexedBook> findByLocationIntersects(Geometry geometry);

    List<IndexedBook> findByLocationGeoWithin(Geometry area);

    List<IndexedBook> findByLocationGeoIntersects(Geometry geometry);

    List<IndexedBook> findByTitleAndPages(String title, int pages);
}
