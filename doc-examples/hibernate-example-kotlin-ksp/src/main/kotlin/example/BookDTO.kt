
package example

import io.micronaut.core.annotation.Introspected

@Introspected
data class BookDTO(
    var title: String,
    var pages: Int
)
