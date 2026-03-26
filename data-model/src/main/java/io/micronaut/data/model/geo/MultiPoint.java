package io.micronaut.data.model.geo;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.GeometryJsonConverter;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record MultiPoint(List<Point> points) implements Geometry {

    public MultiPoint {
        if (CollectionUtils.isEmpty(points)) {
            throw new IllegalArgumentException("MultiPoint requires at least one Point");
        }
    }

    public List<List<Double>> asCoords() {
        return points.stream()
            .map(Point::asCoords)
            .toList();
    }

    public static MultiPoint fromCoords(List<List<Double>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        return new MultiPoint(coords.stream().map(Point::fromCoords).toList());
    }
}
