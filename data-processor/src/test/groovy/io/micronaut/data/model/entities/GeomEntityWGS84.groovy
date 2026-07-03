package io.micronaut.data.model.entities

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Srid
import io.micronaut.data.model.geo.Point

@MappedEntity
class GeomEntityWGS84 {

    @Id
    @GeneratedValue
    private Long id

    @Srid(value = 4326, type = Srid.CrsType.GEOGRAPHIC)
    @Index(columns = "location")
    @MappedProperty("location")
    private Point point

    Long getId() {
        return id
    }

    void setId(Long id) {
        this.id = id
    }

    Point getPoint() {
        return point
    }

    void setPoint(Point point) {
        this.point = point
    }
}
