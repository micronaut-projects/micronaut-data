package io.micronaut.data.model.geo;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.GeometryJsonConverter;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record MultiLineString(List<LineString> lineStrings) implements Geometry {

    public MultiLineString {
        if (CollectionUtils.isEmpty(lineStrings)) {
            throw new IllegalArgumentException("MultiLineString requires at least one LineString");
        }
    }

    public List<List<List<Double>>> asCoords() {
        return lineStrings.stream()
            .map(LineString::asCoords)
            .toList();
    }

    public static MultiLineString fromCoords(List<List<List<Double>>> coords) {
        if (CollectionUtils.isEmpty(coords)) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }
        return new MultiLineString(coords.stream().map(LineString::fromCoords).toList());
    }
}
