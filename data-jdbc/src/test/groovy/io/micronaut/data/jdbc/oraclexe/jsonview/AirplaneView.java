package io.micronaut.data.jdbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Relation;

@JsonView(entity = Airplane.class)
public record AirplaneView (
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    @JsonUnwrapped
    @Relation(Relation.Kind.EMBEDDED)
    AirplaneDetails airplaneDetails
) {}
