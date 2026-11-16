package io.micronaut.data.oracle.graph.fans;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node(value = "fans", labels = "Fan")
public record Fan(@Id Long id,
                  @MappedProperty("first name") String firstName,
                  @Relationship("LIKES") List<Film> films,
                  @Relationship("LIKES") List<Novel> novels) {
}
