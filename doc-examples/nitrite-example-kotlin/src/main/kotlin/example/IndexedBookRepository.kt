package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Polygon

@NitriteRepository
interface IndexedBookRepository : CrudRepository<IndexedBook, String> {

    @Query("{\"description\": {\"\$text\": :text}}")
    fun searchByDescription(text: String): List<IndexedBook>

    // tag::near-query[]
    @Query("{\"location\": {\"\$near\": {\"center\": :location, \"distance\": :maxDistance}}}")
    fun findByLocationNear(location: Geometry, maxDistance: Double): List<IndexedBook>
    // end::near-query[]

    // tag::within-query[]
    @Query("{\"location\": {\"\$within\": :area}}")
    fun findByLocationWithin(area: Polygon): List<IndexedBook>
    // end::within-query[]

    // tag::intersects-query[]
    @Query("{\"location\": {\"\$intersects\": :geometry}}")
    fun findByLocationIntersects(geometry: LineString): List<IndexedBook>
    // end::intersects-query[]
}
