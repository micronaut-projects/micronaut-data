package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public record City(
        @Id @GeneratedValue @Nullable
        Long id,
        String name) {

    public City(String name) {
        this(null, name);
    }
}