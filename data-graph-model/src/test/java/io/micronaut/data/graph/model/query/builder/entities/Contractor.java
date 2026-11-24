package io.micronaut.data.graph.model.query.builder.entities;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

@Node
public record Contractor(@Id String name,
                         @Nullable @Relationship("CONTRACTED_BY") Contract contract,
                         @Nullable @Relationship("EMPLOYED_BY") Employment employment) {
}
