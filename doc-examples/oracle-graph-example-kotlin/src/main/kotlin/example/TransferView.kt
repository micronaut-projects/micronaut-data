package example

import io.micronaut.core.annotation.Introspected
import java.math.BigDecimal

// tag::view[]
@Introspected
data class TransferView(val source: String, val target: String, val amount: BigDecimal)
// end::view[]
