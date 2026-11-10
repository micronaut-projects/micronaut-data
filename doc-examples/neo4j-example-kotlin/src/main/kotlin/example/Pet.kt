package example

// tag::pet[]
import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.DynamicAttributes
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.model.DynamicAttributeHolder

@Node
data class Pet(
    @field:Id
    val name: String,
    @DynamicAttributes // <2>
    val attributes: MutableMap<String, Any?> = mutableMapOf()
) : DynamicAttributeHolder { // <1>

    override fun dynamicAttributes(): MutableMap<String, Any?> = attributes // <3>
}
// end::pet[]
