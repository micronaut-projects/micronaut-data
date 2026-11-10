package example

// tag::pet[]
import io.micronaut.data.annotation.Id
import io.micronaut.data.graph.annotation.DynamicAttributes
import io.micronaut.data.graph.annotation.Node
import io.micronaut.data.graph.model.DynamicAttributeHolder

@Node
class Pet implements DynamicAttributeHolder { // <1>

    @Id
    String name

    @DynamicAttributes // <2>
    Map<String, Object> attributes = [:]

    @Override
    Map<String, Object> dynamicAttributes() { // <3>
        attributes
    }
}
// end::pet[]
