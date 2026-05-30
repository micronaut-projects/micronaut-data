package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Polygon

@NitriteRepository
interface IndexedBookRepository extends CrudRepository<IndexedBook, String> {

    @Query('{"description": {"$text": :text}}')
    List<IndexedBook> searchByDescription(String text)

    // tag::near-query[]
    @Query('{"location": {"$near": {"center": :location, "distance": :maxDistance}}}')
    List<IndexedBook> findByLocationNear(Geometry location, double maxDistance)
    // end::near-query[]

    // tag::within-query[]
    @Query('{"location": {"$within": :area}}')
    List<IndexedBook> findByLocationWithin(Polygon area)
    // end::within-query[]

    // tag::intersects-query[]
    @Query('{"location": {"$intersects": :geometry}}')
    List<IndexedBook> findByLocationIntersects(LineString geometry)
    // end::intersects-query[]
}
