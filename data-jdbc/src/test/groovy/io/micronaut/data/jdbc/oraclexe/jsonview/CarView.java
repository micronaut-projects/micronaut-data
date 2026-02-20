package io.micronaut.data.jdbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;
import java.util.List;

@JsonView(entity = Car.class)
public record CarView (
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    @JsonUnwrapped
    @Relation(Relation.Kind.ONE_TO_ONE)
    CarDetailsSubView carDetails
) {}
