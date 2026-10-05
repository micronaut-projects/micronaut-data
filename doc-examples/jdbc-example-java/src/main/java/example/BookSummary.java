package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record BookSummary(String title, int pages) {
}
