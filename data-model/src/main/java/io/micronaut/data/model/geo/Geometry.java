package io.micronaut.data.model.geo;

public sealed interface Geometry permits Point, MultiPoint, LineString,
    MultiLineString, Polygon, MultiPolygon, GeometryCollection {
}
