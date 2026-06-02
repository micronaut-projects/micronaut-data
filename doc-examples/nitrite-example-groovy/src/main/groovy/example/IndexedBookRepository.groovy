package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import org.locationtech.jts.geom.Geometry

// tag::text-query[]
@NitriteRepository
interface IndexedBookRepository extends CrudRepository<IndexedBook, String> {

    /**
     * Search books by description text (full-text search).
     */
    @Query('{"description": {"$text": :text}}')
    List<IndexedBook> searchByDescription(String text)
    // end::text-query[]

    /**
     * Find books near a location using spatial $near filter.
     */
    // tag::near-query[]
    @Query('{"location": {"$near": {"center": :location, "distance": :maxDistance}}}')
    List<IndexedBook> findByLocationNear(Geometry location, double maxDistance)
    // end::near-query[]

    /**
     * Find books within a geometry using spatial $within filter.
     */
    // tag::within-query[]
    @Query('{"location": {"$within": :area}}')
    List<IndexedBook> findByLocationWithin(Geometry area)
    // end::within-query[]

    /**
     * Find books intersecting a geometry using spatial $intersects filter.
     */
    // tag::intersects-query[]
    @Query('{"location": {"$intersects": :geometry}}')
    List<IndexedBook> findByLocationIntersects(Geometry geometry)
    // end::intersects-query[]
}
