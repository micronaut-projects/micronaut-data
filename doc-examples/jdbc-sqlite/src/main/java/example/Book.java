package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.Nullable;

@MappedEntity
public record Book(@Id @GeneratedValue @Nullable Long id, String title) {
}
