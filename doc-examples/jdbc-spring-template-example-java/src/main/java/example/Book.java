package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

// tag::book[]
@MappedEntity
public record Book(
    @Id @GeneratedValue @Nullable Long id,
    String title,
    int pages
) { }
// end::book[]
