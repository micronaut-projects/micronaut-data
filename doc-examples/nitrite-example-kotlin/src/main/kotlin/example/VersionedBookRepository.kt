package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

// tag::versioned-repository-declaration[]
@NitriteRepository
interface VersionedBookRepository : CrudRepository<VersionedBook, String> {
// end::versioned-repository-declaration[]

// tag::versioned-repository[]
    // Partial update with version check
    fun updateTitle(@Id id: String, title: String, version: Long?)

    // Partial delete with version check
    fun delete(@Id id: String, version: Long?)
    // end::versioned-repository[]
// tag::versioned-repository-declaration[]
}
// end::versioned-repository-declaration[]
