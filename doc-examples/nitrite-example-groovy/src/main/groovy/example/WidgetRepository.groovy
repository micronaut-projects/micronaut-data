package example

import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

import java.util.UUID

@NitriteRepository
interface WidgetRepository extends CrudRepository<Widget, UUID> {
    List<Widget> findByName(String name)
}
