package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Srid
import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.Point

//tag::get[]
@MappedEntity
data class GeographyEntityJson(
    //end::get[]
    @field:Id
    @field:GeneratedValue
    val id: Long? = null,
    //tag::get[]
    @field:Srid(4258)
    @field:Index(columns = ["location"])
    @MappedProperty(value = "location", definition = "geography not null")
    val point: Point,

    @MappedProperty(definition = "geography")
    val multiPoint: MultiPoint? = null
)
//end::get[]
