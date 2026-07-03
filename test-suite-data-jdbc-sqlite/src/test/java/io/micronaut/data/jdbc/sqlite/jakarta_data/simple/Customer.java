package io.micronaut.data.jdbc.sqlite.jakarta_data.simple;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

import static io.micronaut.data.annotation.Relation.Kind.EMBEDDED;

@Introspected
@MappedEntity(value = "customers")
@Serdeable
public record Customer(
        @Id @AutoPopulated UUID id,
        String name,
        Integer age,
        @Relation(EMBEDDED) Address address,
        @Version Long version
) {
    public static Customer of(String name, Integer age, Address address) {
        return new Customer(null, name, age, address, null);
    }
}
