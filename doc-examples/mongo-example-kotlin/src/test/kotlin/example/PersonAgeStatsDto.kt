package example

import io.micronaut.core.annotation.Introspected

@Introspected
class PersonAgeStatsDto(
        var maxAge: Int,
        var minAge: Int,
        var avgAge: Double
)
