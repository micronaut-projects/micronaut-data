package example

import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar
import io.micronaut.data.graph.runtime.GraphValueEncoders
import jakarta.inject.Singleton

// tag::registrar[]
@Singleton
class MoneyEncoderRegistrar implements GraphValueEncoderRegistrar {

    @Override
    void register(GraphValueEncoders encoders) {
        encoders.register(Money, { Money money, GraphValueEncoders e -> // <1>
            [amount: e.encode(money.amount), currency: money.currency] // <2>
        })
    }
}
// end::registrar[]
