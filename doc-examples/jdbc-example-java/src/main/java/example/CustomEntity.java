package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("${entity.prefix}entity")
public record CustomEntity(
    @Id
    @GeneratedValue
    Long id,
    String name) {
}
