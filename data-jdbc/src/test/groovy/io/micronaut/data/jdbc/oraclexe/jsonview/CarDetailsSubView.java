package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonSubView;

@JsonSubView(entity = CarDetails.class)
public record CarDetailsSubView (
    @Id
    @GeneratedValue
    Long id,
    String model,
    Long year
) {}
