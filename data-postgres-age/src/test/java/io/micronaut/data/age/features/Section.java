package io.micronaut.data.age.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

@Node
public record Section(@Id String name) {
}
