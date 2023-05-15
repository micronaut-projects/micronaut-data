package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public record CarManufacturer3(@Id Long id, String name, @Nullable Car car) {
}
