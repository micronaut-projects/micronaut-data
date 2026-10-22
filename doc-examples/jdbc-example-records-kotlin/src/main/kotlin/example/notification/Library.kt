package example.notification

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Srid
import io.micronaut.data.model.geo.Point

@MappedEntity
data class Library(
    @field:Id @field:GeneratedValue val id: Long?,
    val name: String,
    val email: String,
    val capacity: Int,
    @field:Srid(4326) @field:Index(columns = ["location"]) val location: Point
)
