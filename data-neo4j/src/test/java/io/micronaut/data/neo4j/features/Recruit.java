package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

/**
 * An immutable versioned node that relates to nodes of its own type.
 */
@Node
public record Recruit(@Id @GeneratedValue String id,
                      @Version Long version,
                      String name,
                      @Relationship("BUDDY") List<Recruit> buddies) {
}
