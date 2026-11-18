package io.micronaut.data.graph.runtime.entities;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.DynamicAttributes;
import io.micronaut.data.graph.annotation.Labels;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Node
@Serdeable
public record Item(@Id String name, @DynamicAttributes Map<String, Object> attributes, @Labels List<String> labels) {
}
