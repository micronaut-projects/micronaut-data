package io.micronaut.data.document.mongodb.entities;

import com.mongodb.client.model.geojson.LineString;
import com.mongodb.client.model.geojson.MultiLineString;
import com.mongodb.client.model.geojson.MultiPoint;
import com.mongodb.client.model.geojson.Point;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class GeoEntity {

    @Id
    @GeneratedValue
    private String id;

    private Point point;

    private MultiPoint multiPoint;

    private LineString lineString;

    @Nullable
    private MultiLineString multiLineString;

    public String getId() {
        return id;
    }

    public void setId(String id) {
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
}
