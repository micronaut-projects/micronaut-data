package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue

@Embeddable
data class Part(
    @GeneratedValue
    val text: String? = null,
)
