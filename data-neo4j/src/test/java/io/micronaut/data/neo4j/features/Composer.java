package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.NamingStrategy;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.model.naming.NamingStrategies;

@Node
@NamingStrategy(NamingStrategies.UnderScoreSeparatedLowerCase.class)
public record Composer(@Id String fullName, Integer birthYear, @MappedProperty("opus_count") Integer works) {
}
