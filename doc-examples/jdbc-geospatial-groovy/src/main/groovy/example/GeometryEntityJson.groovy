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

//tag::get[]
@MappedEntity
class GeometryEntityJson {
    //end::get[]

    @Id
    @GeneratedValue
    Long id
    //tag::get[]

    @Srid(3857)
    @Index(columns = "location")
    @MappedProperty("location")
    Point point

    @Nullable
    MultiPoint multiPoint
}
//end::get[]
