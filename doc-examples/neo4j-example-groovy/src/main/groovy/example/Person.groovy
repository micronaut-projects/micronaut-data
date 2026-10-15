package example

// tag::person[]
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
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
}
// end::person[]
