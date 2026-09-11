package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import java.time.Instant

// tag::versioned-temporal-repository[]
@NitriteRepository
interface VersionedBookTemporalRepository : CrudRepository<VersionedBookTemporal, String> {

    // Partial update with version check
    fun updateTitle(@Id id: String, title: String, version: Instant?)

    // Partial delete with version check
    fun delete(@Id id: String, version: Instant?)
}
// end::versioned-temporal-repository[]
