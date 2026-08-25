package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.LinkedHashSet;
import java.util.Set;

@MappedEntity
public class Member {
    @Id
    @GeneratedValue
    private String id;

    private String name;

    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "members")
    private Set<Club> clubs = new LinkedHashSet<>();

    public Member() {
    }

    public Member(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Club> getClubs() {
        return clubs;
    }

    public void setClubs(Set<Club> clubs) {
        this.clubs = clubs;
    }
}
