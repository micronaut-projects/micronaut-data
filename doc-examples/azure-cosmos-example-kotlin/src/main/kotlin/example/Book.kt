
package example

import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import java.util.*

// tag::book[]
@MappedEntity
data class Book(@field:Id
                @GeneratedValue
                var id: String?,
                var title: String,
                var pages: Int = 0,
                @DateCreated
                var createdDate: Date? = null,
                @DateUpdated
                var updatedDate: Date? = null)
// end::book[]
