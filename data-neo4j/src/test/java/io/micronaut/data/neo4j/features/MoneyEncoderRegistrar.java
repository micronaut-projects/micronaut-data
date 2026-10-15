package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.runtime.GraphValueEncoderRegistrar;
import io.micronaut.data.graph.runtime.GraphValueEncoders;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class MoneyEncoderRegistrar implements GraphValueEncoderRegistrar {

    @Override
    public void register(GraphValueEncoders encoders) {
        encoders.register(Money.class, (money, e) -> Map.of(
            "amount", e.encode(money.amount()),
            "currency", money.currency()
        ));
    }
}
