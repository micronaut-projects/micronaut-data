package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Srid
import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.Point
import io.micronaut.data.model.runtime.convert.GeometryWktConverter

//tag::get[]
@MappedEntity
data class GeometryEntityWkt(
    //end::get[]
    @field:Id
    @field:GeneratedValue
    val id: Long? = null,
    //tag::get[]
    @field:Srid(Srid.WEB_MERCATOR)
    @field:Index(columns = ["location"])
    @MappedProperty(value = "location", converter = GeometryWktConverter::class)
    val point: Point,

    @MappedProperty(converter = GeometryWktConverter::class)
    val multiPoint: MultiPoint? = null
)
//end::get[]
