package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity

// tag::unique-index[]
@MappedEntity
class CatalogItem {
    @Id
    @GeneratedValue
    var id: String? = null

    @Index(columns = ["sku"], unique = true)
    var sku: String? = null

    var name: String? = null

    constructor()

    constructor(sku: String, name: String) {
        this.sku = sku
        this.name = name
    }
}
// end::unique-index[]
