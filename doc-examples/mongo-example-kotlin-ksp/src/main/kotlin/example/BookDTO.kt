
package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class BookDTO(
    var title: String,
    var pages: Int
)
