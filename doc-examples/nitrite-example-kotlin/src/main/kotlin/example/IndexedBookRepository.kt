package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import org.locationtech.jts.geom.Geometry

// tag::text-query[]
@NitriteRepository
interface IndexedBookRepository : CrudRepository<IndexedBook, String> {

    /**
     * Search books by description text (full-text search).
     */
    @Query("{\"description\": {\"\$text\": :text}}")
    fun searchByDescription(text: String?): List<IndexedBook>
    // end::text-query[]

    /**
     * Find books near a location using spatial \$near filter.
    */
    // tag::near-query[]
    @Query("{\"location\": {\"\$near\": {\"center\": :location, \"distance\": :maxDistance}}}")
    fun findByLocationNearQuery(location: Geometry?, maxDistance: Double): List<IndexedBook>
    // end::near-query[]

    // tag::derived-spatial[]
    fun findByLocationNear(location: Geometry?, maxDistance: Double): List<IndexedBook>

    /**
     * Find books within a geometry using spatial \$within filter.
     */
    // tag::within-query[]
    @Query("{\"location\": {\"\$within\": :area}}")
    fun findByLocationWithin(area: Geometry?): List<IndexedBook>
    // end::within-query[]

    fun findByLocationGeoWithin(area: Geometry?): List<IndexedBook>

    /**
     * Find books intersecting a geometry using spatial \$intersects filter.
     */
    // tag::intersects-query[]
    @Query("{\"location\": {\"\$intersects\": :geometry}}")
    fun findByLocationIntersects(geometry: Geometry?): List<IndexedBook>
    // end::intersects-query[]

    fun findByLocationGeoIntersects(geometry: Geometry?): List<IndexedBook>
    // end::derived-spatial[]
}
