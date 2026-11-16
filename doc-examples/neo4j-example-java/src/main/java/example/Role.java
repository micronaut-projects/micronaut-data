package example;

// tag::role[]
import io.micronaut.data.graph.annotation.RelationshipId;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

import java.util.List;

@RelationshipProperties // <1>
public class Role {

    @RelationshipId // <2>
    private Long id;

    private final List<String> roles; // <3>

    @TargetNode // <4>
    private final Person person;

    public Role(List<String> roles, Person person) {
        this.roles = roles;
        this.person = person;
    }
    // end::role[]

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<String> getRoles() {
        return roles;
    }

    public Person getPerson() {
        return person;
    }
// tag::role[]
}
// end::role[]
