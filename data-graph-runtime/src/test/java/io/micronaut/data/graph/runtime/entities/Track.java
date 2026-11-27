package io.micronaut.data.graph.runtime.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.NamingStrategy;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.serde.annotation.Serdeable;

@Node
@NamingStrategy(NamingStrategies.UnderScoreSeparatedLowerCase.class)
@Serdeable
public record Track(@Id String trackName, Integer playCount, @MappedProperty("seconds") Integer length) {
}
