package example

// tag::article[]
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.Version
import io.micronaut.data.graph.annotation.Node

@Node
@Index(columns = ["author"]) // <1>
data class Article(
    @field:Id // <2>
    val slug: String,
    var title: String,
    val author: String,
    @field:Version // <3>
    var version: Long? = null
)
// end::article[]
