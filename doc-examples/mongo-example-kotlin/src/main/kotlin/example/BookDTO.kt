
package example

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@Introspected
data class BookDTO(
    var title: String,
    var pages: Int
)
