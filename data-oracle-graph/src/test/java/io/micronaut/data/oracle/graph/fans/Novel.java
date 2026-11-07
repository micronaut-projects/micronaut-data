package io.micronaut.data.oracle.graph.fans;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node(value = "novels", labels = "Novel")
@MappedEntity(value = "novels", schema = "TEST")
public record Novel(@Id Long id,
                    String title,
                    @Relationship(type = "SEQUEL", direction = Direction.INCOMING) List<Novel> prequels,
                    @Relationship("SEQUEL") List<Novel> sequels) {
}
