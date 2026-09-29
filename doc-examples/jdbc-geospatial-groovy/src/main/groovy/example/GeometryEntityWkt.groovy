package example

import io.micronaut.core.annotation.Nullable
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
class GeometryEntityWkt {
    //end::get[]

    @Id
    @GeneratedValue
    Long id
    //tag::get[]

    @Srid(3857)
    @Index(columns = "location")
    @MappedProperty(value = "location", converter = GeometryWktConverter)
    Point point

    @Nullable
    @MappedProperty(converter = GeometryWktConverter)
    MultiPoint multiPoint
}
//end::get[]
