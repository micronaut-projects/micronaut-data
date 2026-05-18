package example;

import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.List;

@MappedEntity
public record CarManufacturer4(@Id Long id, String name, @Nullable List<Car> cars) {
}
