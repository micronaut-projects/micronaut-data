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
data class GeometryEntityJson(
    //end::get[]
    @field:Id
    @field:GeneratedValue
    val id: Long? = null,
    //tag::get[]
    @field:Srid(Srid.WEB_MERCATOR)
    @field:Index(columns = ["location"])
    @MappedProperty("location")
    val point: Point,

    val multiPoint: MultiPoint? = null
)
//end::get[]
