package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node
public record Movie(@Id String title,
                    Integer released,
                    @Relationship(type = "DIRECTED", direction = Direction.INCOMING) List<Person> directors) {
}
