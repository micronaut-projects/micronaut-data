package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class PersonAgeStatsDto(
        var maxAge: Int,
        var minAge: Int,
        var avgAge: Double
)
