package io.micronaut.data.model.entities

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.geo.Point

@MappedEntity
class GeomEntityCompositeIndex {

    @Id
    @GeneratedValue
    private Long id

    @Index(columns = ["point", "name_col"])
    @MappedProperty("point")
    private Point point

    @MappedProperty("name_col")
    private String name

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

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }
}

