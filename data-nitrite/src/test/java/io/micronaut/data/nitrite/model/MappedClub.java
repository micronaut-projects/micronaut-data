package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

import java.util.LinkedHashSet;
import java.util.Set;

@MappedEntity
public class MappedClub {
    @Id
    @GeneratedValue
    @MappedProperty("club_id")
    private String id;

    private String name;

    @Relation(Relation.Kind.MANY_TO_MANY)
    private Set<MappedMember> members = new LinkedHashSet<>();

    public MappedClub() {
    }

    public MappedClub(String name) {
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

    public Set<MappedMember> getMembers() {
        return members;
    }

    public void setMembers(Set<MappedMember> members) {
        this.members = members;
    }
}
