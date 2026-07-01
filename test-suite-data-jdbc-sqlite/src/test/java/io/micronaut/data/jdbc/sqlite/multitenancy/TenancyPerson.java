package io.micronaut.data.jdbc.sqlite.multitenancy;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.data.model.naming.NamingStrategies;

@Introspected
@MappedEntity(value = "persons", namingStrategy = NamingStrategies.Raw.class)
record TenancyPerson(
    @Id
    Integer id,
    String firstName,
    String lastName,
    @TenantId String tenantId) {
}
