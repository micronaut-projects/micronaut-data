package io.micronaut.data.jdbc.sqlite.autopopulate;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.Embeddable;

import java.time.LocalDateTime;
import java.util.UUID;

@Embeddable
record InnerFields(
    @DateCreated
    LocalDateTime subInnerCreatedAt,
    @AutoPopulated
    UUID subInnerGuid
) {
    public InnerFields() {
        this(null, null);
    }
}
