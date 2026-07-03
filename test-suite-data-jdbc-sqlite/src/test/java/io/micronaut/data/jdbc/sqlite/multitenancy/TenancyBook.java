package io.micronaut.data.jdbc.sqlite.multitenancy;

import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable // <1>
@MappedEntity // <2>
record TenancyBook(@Nullable
                          @Id // <3>
                          @GeneratedValue // <4>
                          Long id,
                          String title,
                          @TenantId // <5>
                          String framework) {
}
