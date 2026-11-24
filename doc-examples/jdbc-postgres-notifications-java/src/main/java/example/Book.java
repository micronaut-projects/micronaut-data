package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

// tag::book[]
@Serdeable
@MappedEntity(value = "book", schema = "public")
public record Book(@Id Long id, String title) {
}
// end::book[]
