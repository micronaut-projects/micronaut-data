package io.micronaut.data.model.geo;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.GeometryJsonConverter;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@TypeDef(type = DataType.STRING, converter = GeometryJsonConverter.class)
public record GeometryCollection(List<Geometry> geometries) implements Geometry {

    public GeometryCollection {
        if (CollectionUtils.isEmpty(geometries)) {
            throw new IllegalArgumentException("GeometryCollection requires at least one Geometry");
        }
    }
}
