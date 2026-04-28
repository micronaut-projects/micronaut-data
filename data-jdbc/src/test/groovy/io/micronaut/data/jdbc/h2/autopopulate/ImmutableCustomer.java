package io.micronaut.data.jdbc.h2.autopopulate;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;

import java.util.UUID;

@Introspected
@MappedEntity("immutable_customer_ap")
public record ImmutableCustomer(
    @Id @AutoPopulated(skipIfPresent = true) UUID id,
    String name,
    @Version Long version
) {
}
