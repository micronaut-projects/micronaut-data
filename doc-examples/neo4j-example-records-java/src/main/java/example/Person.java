package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

@Node
public record Person(@Id String name, int born) {
}
