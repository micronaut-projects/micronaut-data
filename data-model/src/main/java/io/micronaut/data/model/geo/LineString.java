package io.micronaut.data.model.geo;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.GeometryConverter;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryConverter.class)
public record LineString(List<Point> points) implements Geometry {

    public LineString {
        if (CollectionUtils.isEmpty(points) || points.size() < 2) {
            throw new IllegalArgumentException("At least 2 points required for a LineString");
        }
    }

    public List<List<Double>> asCoords() {
        return points.stream()
            .map(Point::asCoords)
            .toList();
    }

    public static LineString fromCoords(List<List<Double>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        return new LineString(coords.stream().map(Point::fromCoords).toList());
    }
}
