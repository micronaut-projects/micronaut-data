package example;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;

// tag::view[]
@Serdeable
public record TransferView(String source, String target, BigDecimal amount) {
}
// end::view[]
