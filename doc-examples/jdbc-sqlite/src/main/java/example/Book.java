package example;

import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.*;

@MappedEntity
public record Book(@Id @GeneratedValue @Nullable Long id, String title) {
}
