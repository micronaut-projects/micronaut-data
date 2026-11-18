package example

import io.micronaut.serde.annotation.Serdeable
import java.math.BigDecimal

// tag::view[]
@Serdeable
data class TransferView(val source: String, val target: String, val amount: BigDecimal)
// end::view[]
