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
    String id

    @Index(columns = "sku", unique = true)
    String sku

    String name

    CatalogItem() {
    }

    CatalogItem(String sku, String name) {
        this.sku = sku
        this.name = name
    }
}
// end::unique-index[]
