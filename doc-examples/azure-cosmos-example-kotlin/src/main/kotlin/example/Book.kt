
package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::book[]
@MappedEntity
data class Book(@field:Id
                @GeneratedValue
                var id: String?,
                var title: String,
                var pages: Int = 0)
// end::book[]
