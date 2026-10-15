package example;

// tag::pet[]
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.DynamicAttributes;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.model.DynamicAttributeHolder;

import java.util.HashMap;
import java.util.Map;

@Node
public class Pet implements DynamicAttributeHolder { // <1>

    @Id
    private final String name;

    @DynamicAttributes // <2>
    private Map<String, Object> attributes = new HashMap<>();

    public Pet(String name) {
        this.name = name;
    }

    @Override
    public Map<String, Object> dynamicAttributes() { // <3>
        return attributes;
    }
    // end::pet[]

    public String getName() {
        return name;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
// tag::pet[]
}
// end::pet[]
