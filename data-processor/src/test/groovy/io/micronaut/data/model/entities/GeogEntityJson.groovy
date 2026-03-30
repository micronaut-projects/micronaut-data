package io.micronaut.data.model.entities

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Srid
import io.micronaut.data.model.geo.LineString
import io.micronaut.data.model.geo.MultiLineString
import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.Point

@MappedEntity
class GeogEntityJson {

    @Id
    @GeneratedValue
    private Long id;

    @Srid(3857)
    @Index(columns = "location")
    @MappedProperty(value = "location", definition = "GEOGRAPHY NOT NULL")
    private Point point;

    @Index(columns = "multi_point")
    @MappedProperty(definition = "GEOGRAPHY NOT NULL")
    private MultiPoint multiPoint;

    @Srid(3857)
    @MappedProperty(definition = "GEOGRAPHY NOT NULL")
    private LineString lineString;

    @Nullable
    @MappedProperty(definition = "GEOGRAPHY")
    private MultiLineString multiLineString;

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

    MultiPoint getMultiPoint() {
        return multiPoint
    }

    void setMultiPoint(MultiPoint multiPoint) {
        this.multiPoint = multiPoint
    }

    LineString getLineString() {
        return lineString
    }

    void setLineString(LineString lineString) {
        this.lineString = lineString
    }

    MultiLineString getMultiLineString() {
        return multiLineString
    }

    void setMultiLineString(MultiLineString multiLineString) {
        this.multiLineString = multiLineString
    }
}
