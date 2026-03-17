package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

// tag::versioned-repository[]
@NitriteRepository
interface VersionedBookRepository : CrudRepository<VersionedBook, String> {

    // Partial update with version check
    fun updateTitle(@Id id: String, title: String, version: Long?)

    // Partial delete with version check
    fun delete(@Id id: String, version: Long?)
}
// end::versioned-repository[]
