package example

import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar
import io.micronaut.data.graph.runtime.GraphValueEncoders
import jakarta.inject.Singleton

// tag::registrar[]
@Singleton
class MoneyEncoderRegistrar : GraphValueEncoderRegistrar {

    override fun register(encoders: GraphValueEncoders) {
        encoders.register(Money::class.java) { money, e -> // <1>
            mapOf("amount" to e.encode(money.amount), "currency" to money.currency) // <2>
        }
    }
}
// end::registrar[]
