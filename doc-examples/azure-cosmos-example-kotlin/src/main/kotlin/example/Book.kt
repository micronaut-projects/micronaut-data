
package example

import io.micronaut.data.annotation.*
import java.util.*

// tag::book[]
@MappedEntity
data class Book(@field:Id
                @GeneratedValue
                var id: String?,
                var title: String,
                var pages: Int = 0,
                @MappedProperty(converter = ItemPriceAttributeConverter::class)
                var itemPrice: ItemPrice? = null,
                @DateCreated
                var createdDate: Date? = null,
                @DateUpdated
                var updatedDate: Date? = null)
// end::book[]
