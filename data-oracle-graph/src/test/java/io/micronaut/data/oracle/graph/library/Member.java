package io.micronaut.data.oracle.graph.library;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

@Node(value = "members", labels = "Member")
public record Member(@Id Long id,
                     String name,
                     @Relationship("FOLLOWS") List<Member> follows,
                     @Relationship("BORROWED") List<Loan> loans) {
}
