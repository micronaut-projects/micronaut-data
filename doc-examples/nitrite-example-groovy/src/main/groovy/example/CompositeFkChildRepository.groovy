package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

// tag::composite-fk-repository[]
@NitriteRepository
interface CompositeFkChildRepository extends CrudRepository<CompositeFkChild, String> {
    @Join("parent")
    Optional<CompositeFkChild> findByName(String name)
}
// end::composite-fk-repository[]
