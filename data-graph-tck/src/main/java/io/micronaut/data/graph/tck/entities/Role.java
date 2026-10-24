package io.micronaut.data.graph.tck.entities;

import io.micronaut.data.graph.annotation.RelationshipId;
import io.micronaut.data.graph.annotation.RelationshipProperties;
import io.micronaut.data.graph.annotation.TargetNode;

import java.util.List;

@RelationshipProperties
public class Role {
    @RelationshipId
    private Long id;
    private final List<String> roles;
    @TargetNode
    private final Person person;

    public Role(List<String> roles, Person person) {
        this.roles = roles;
        this.person = person;
    }

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
}
