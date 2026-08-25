package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

import java.util.LinkedHashSet;
import java.util.Set;

@MappedEntity
public class MappedMember {
    @Id
    @GeneratedValue
    @MappedProperty("member_id")
    private String id;

    private String name;

    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "members")
    private Set<MappedClub> clubs = new LinkedHashSet<>();

    public MappedMember() {
    }

    public MappedMember(String name) {
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

    public Set<MappedClub> getClubs() {
        return clubs;
    }

    public void setClubs(Set<MappedClub> clubs) {
        this.clubs = clubs;
    }
}
