package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;

@JsonSubView(entity = CarDetails.class, operations = { JsonView.Operation.UPDATE, JsonView.Operation.INSERT })
public record CarDetailsSubView (
    @Id
    @GeneratedValue
    Long id,
    String model,
    Long year
) {}
