package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node("Account")
public record Account(@Id Long id, String name, @Relationship("TRANSFERS") List<Transfer> transfers) {
}
