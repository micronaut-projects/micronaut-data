package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;

// tag::person[]
@Node // <1>
public record Person(@Id String name, // <2>
                     Integer born) {
}
// end::person[]
