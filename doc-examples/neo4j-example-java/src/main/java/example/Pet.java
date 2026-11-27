package example;

import io.micronaut.data.graph.annotation.DynamicRelationships;

import java.util.List;

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

    // tag::relationships[]
    @DynamicRelationships // <1>
    private Map<String, List<Object>> related = new HashMap<>();
    // end::relationships[]

    public Map<String, List<Object>> getRelated() {
        return related;
    }

    public void setRelated(Map<String, List<Object>> related) {
        this.related = related;
    }
// tag::pet[]
}
// end::pet[]
