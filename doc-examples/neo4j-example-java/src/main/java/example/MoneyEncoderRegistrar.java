package example;

import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar;
import io.micronaut.data.graph.runtime.GraphValueEncoders;
import jakarta.inject.Singleton;

import java.util.Map;

// tag::registrar[]
@Singleton
public class MoneyEncoderRegistrar implements GraphValueEncoderRegistrar {

    @Override
    public void register(GraphValueEncoders encoders) {
        encoders.register(Money.class, (money, e) -> Map.of( // <1>
            "amount", e.encode(money.amount()), // <2>
            "currency", money.currency()
        ));
    }
}
// end::registrar[]
