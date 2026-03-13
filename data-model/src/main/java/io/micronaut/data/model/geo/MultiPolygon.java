package io.micronaut.data.model.geo;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.GeometryConverter;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryConverter.class)
public record MultiPolygon(List<Polygon> polygons) implements Geometry {

    public List<List<List<List<Double>>>> asCoords() {
        return polygons.stream()
            .map(Polygon::asCoords)
            .toList();
    }

    public static MultiPolygon fromCoords(List<List<List<List<Double>>>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        return new MultiPolygon(coords.stream().map(Polygon::fromCoords).toList());
    }
}
