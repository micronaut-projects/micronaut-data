package example

import io.micronaut.core.annotation.Introspected

// tag::view[]
@Introspected
class TransferView {
    String source
    String target
    BigDecimal amount
}
// end::view[]
