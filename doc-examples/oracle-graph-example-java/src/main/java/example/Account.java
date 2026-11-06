package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.List;

// tag::account[]
@Node("Account") // <1>
public record Account(@Id Long id, // <2>
                      String name,
                      @Relationship("TRANSFERS") List<Transfer> transfers) { // <3>
}
// end::account[]
