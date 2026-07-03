package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@MappedEntity
public record Person(@Id @GeneratedValue @Nullable Long id, @NonNull String name, @NonNull Integer age) {
}
