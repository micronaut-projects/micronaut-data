package io.micronaut.data.model.geo;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.GeometryJsonConverter;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record Polygon(List<LineString> lineStrings) implements Geometry {

    public Polygon {
        if (CollectionUtils.isEmpty(lineStrings)) {
            throw new IllegalArgumentException("Polygon requires at least one ring (outer boundary)");
        }
        for (int i = 0; i < lineStrings.size(); i++) {
            LineString ring = lineStrings.get(i);
            if (ring.points() == null || ring.points().size() < 4) {
                throw new IllegalArgumentException(
                    String.format("Ring at index %d must have at least 4 points (got %d)", i, ring.points() == null ? 0 : ring.points().size()));
            }
            Point first = ring.points().getFirst();
            Point last = ring.points().getLast();
            if (!first.equals(last)) {
                throw new IllegalArgumentException(String.format("Ring at index %d is not closed: the first point is not equal to the last point", i));
            }
        }
    }

    public List<List<List<Double>>> asCoords() {
        return lineStrings.stream()
            .map(LineString::asCoords)
            .toList();
    }

    public static Polygon fromCoords(List<List<List<Double>>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        return new Polygon(coords.stream().map(LineString::fromCoords).toList());
    }
}
