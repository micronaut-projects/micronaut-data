package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import java.util.Optional

// tag::composite-fk-repository[]
@NitriteRepository
interface CompositeFkChildRepository : CrudRepository<CompositeFkChild, String> {
    @Join("parent")
    fun findByName(name: String): Optional<CompositeFkChild>
}
// end::composite-fk-repository[]
