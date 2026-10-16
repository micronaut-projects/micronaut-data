package example

import io.micronaut.serde.annotation.Serdeable

// tag::view[]
@Serdeable
class TransferView {
    String source
    String target
    BigDecimal amount
}
// end::view[]
