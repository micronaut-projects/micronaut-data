package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public record CarManufacturer1(@Id Long id, String name) {
}
