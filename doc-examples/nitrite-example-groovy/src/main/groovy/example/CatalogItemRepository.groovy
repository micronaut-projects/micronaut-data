package example

import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

@NitriteRepository
interface CatalogItemRepository extends CrudRepository<CatalogItem, String> {
}
