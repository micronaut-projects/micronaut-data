package example

import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import java.util.UUID

@NitriteRepository
interface WidgetRepository : CrudRepository<Widget, UUID> {
    fun findByName(name: String): List<Widget>
}
