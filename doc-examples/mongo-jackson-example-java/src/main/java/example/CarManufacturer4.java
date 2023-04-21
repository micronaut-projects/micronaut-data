package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.List;

@MappedEntity
public record CarManufacturer4(@Id Long id, String name, @Nullable List<Car> cars) {
}
