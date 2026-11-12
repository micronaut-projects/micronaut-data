package example;

import io.micronaut.core.annotation.Introspected;

import java.math.BigDecimal;

// tag::view[]
@Introspected
public record TransferView(String source, String target, BigDecimal amount) {
}
// end::view[]
