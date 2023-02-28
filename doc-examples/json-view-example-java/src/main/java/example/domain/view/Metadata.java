package example.domain.view;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Metadata(
    String etag,
    String asof
) {
}
