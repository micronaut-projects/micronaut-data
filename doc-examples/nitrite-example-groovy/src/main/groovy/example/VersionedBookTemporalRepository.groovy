package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import java.time.Instant

// tag::versioned-temporal-repository[]
@NitriteRepository
interface VersionedBookTemporalRepository extends CrudRepository<VersionedBookTemporal, String> {

    // Partial update with version check
    void updateTitle(@Id String id, String title, Instant version)

    // Partial delete with version check
    void delete(@Id String id, Instant version)
}
// end::versioned-temporal-repository[]
