package io.micronaut.data.tck.entities.embedded;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import jakarta.persistence.Convert;

@Embeddable
public record ResourceEntity<S extends Enum<S>>(

    String displayName,

    @TypeDef(type = DataType.STRING)
    @Convert(converter = StateConverter.class)
    S state
) {
}

