package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubView(entity = Member.class, alias = "mbrsw")
public record MemberSubView(
    @Id
    Long id,
    String name
) {}
