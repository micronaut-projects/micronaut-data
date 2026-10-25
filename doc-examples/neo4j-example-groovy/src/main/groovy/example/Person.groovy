package example

// tag::person[]
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Relation
import io.micronaut.data.graph.annotation.Labels
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.annotation.Relationship

@Node
class Person {

    @Id
    @AutoPopulated // <1>
    UUID id

    String name

    Integer born

    @Relationship("FOLLOWS") // <2>
    List<Person> follows = []
    // end::person[]

    // tag::labels[]
    @Labels // <1>
    Set<String> labels = []
    // end::labels[]

    // tag::cascade[]
    @Relationship(value = "KNOWS", cascade = Relation.Cascade.NONE) // <1>
    List<Person> knows = []
    // end::cascade[]
// tag::person[]
}
// end::person[]
