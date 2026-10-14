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
class GeographyEntityWkt {
    //end::get[]

    @Id
    @GeneratedValue
    Long id
    //tag::get[]

    @Srid(Srid.ETRS_89)
    @Index(columns = "location")
    @MappedProperty(value = "location", converter = GeometryWktConverter, definition = "geography not null")
    Point point

    @Nullable
    @MappedProperty(converter = GeometryWktConverter, definition = "geography")
    MultiPoint multiPoint
}
//end::get[]
