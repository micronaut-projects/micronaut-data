package io.micronaut.data.jdbc.oraclexe.notification

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity(value = "catalog_product")
class CatalogProduct {
    @Id
    @GeneratedValue
    Long id

    Long categoryId
}
