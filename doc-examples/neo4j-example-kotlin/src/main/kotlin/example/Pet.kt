package example

import io.micronaut.data.graph.annotation.DynamicRelationships

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
    // end::pet[]

    // tag::relationships[]
    @DynamicRelationships // <1>
    var related: MutableMap<String, List<Any>> = mutableMapOf()
    // end::relationships[]
// tag::pet[]
}
// end::pet[]
