package example

// tag::article[]
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.Version
import io.micronaut.data.graph.annotation.Node

@Node
@Index(columns = "author") // <1>
class Article {

    @Id // <2>
    String slug

    String title

    String author

    @Version // <3>
    Long version
}
// end::article[]
