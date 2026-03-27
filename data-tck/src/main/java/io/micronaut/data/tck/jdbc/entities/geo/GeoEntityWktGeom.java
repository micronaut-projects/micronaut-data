package io.micronaut.data.tck.jdbc.entities.geo;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Srid;
import io.micronaut.data.model.geo.GeometryCollection;
import io.micronaut.data.model.geo.LineString;
import io.micronaut.data.model.geo.MultiLineString;
import io.micronaut.data.model.geo.MultiPoint;
import io.micronaut.data.model.geo.MultiPolygon;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.geo.Polygon;
import io.micronaut.data.model.runtime.convert.GeometryWktConverter;

@MappedEntity
public class GeoEntityWktGeom {

    @Id
    @GeneratedValue
    private Long id;

    @Srid(3857)
    @Index(columns = "location")
    @MappedProperty(value = "location", converter = GeometryWktConverter.class, definition = "geometry")
    private Point point;

    @Srid(3857)
    @Index(columns = "multi_point")
    @MappedProperty(converter = GeometryWktConverter.class, definition = "geometry")
    private MultiPoint multiPoint;

    @Srid(3857)
    @MappedProperty(converter = GeometryWktConverter.class, definition = "geometry")
    private LineString lineString;

    @Nullable
    @MappedProperty(converter = GeometryWktConverter.class, definition = "geometry")
    private MultiLineString multiLineString;

    @Nullable
    @MappedProperty(converter = GeometryWktConverter.class, definition = "geometry")
    private Polygon polygon;

    @Nullable
    @MappedProperty(converter = GeometryWktConverter.class, definition = "geometry")
    private MultiPolygon multiPolygon;

    @Nullable
    @MappedProperty(converter = GeometryWktConverter.class, definition = "geometry")
    private GeometryCollection geometryCollection;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }

    public MultiPoint getMultiPoint() {
        return multiPoint;
    }

    public void setMultiPoint(MultiPoint multiPoint) {
        this.multiPoint = multiPoint;
    }

    public LineString getLineString() {
        return lineString;
    }

    public void setLineString(LineString lineString) {
        this.lineString = lineString;
    }

    public MultiLineString getMultiLineString() {
        return multiLineString;
    }

    public void setMultiLineString(MultiLineString multiLineString) {
        this.multiLineString = multiLineString;
    }

    public Polygon getPolygon() {
        return polygon;
    }

    public void setPolygon(Polygon polygon) {
        this.polygon = polygon;
    }

    public MultiPolygon getMultiPolygon() {
        return multiPolygon;
    }

    public void setMultiPolygon(MultiPolygon multiPolygon) {
        this.multiPolygon = multiPolygon;
    }

    public GeometryCollection getGeometryCollection() {
        return geometryCollection;
    }

    public void setGeometryCollection(GeometryCollection geometryCollection) {
        this.geometryCollection = geometryCollection;
    }
}
