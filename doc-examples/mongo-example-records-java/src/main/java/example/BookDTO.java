
package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record BookDTO(String title, int pages) {
}
