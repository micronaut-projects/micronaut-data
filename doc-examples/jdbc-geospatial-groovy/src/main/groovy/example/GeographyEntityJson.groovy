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
class GeographyEntityJson {
    //end::get[]

    @Id
    @GeneratedValue
    Long id
    //tag::get[]

    @Srid(4258)
    @Index(columns = "location")
    @MappedProperty(value = "location", definition = "geography not null")
    Point point

    @Nullable
    @MappedProperty(definition = "geography")
    MultiPoint multiPoint
}
//end::get[]
