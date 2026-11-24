package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@JsonView(entity = Club.class, alias = "clw")
public class ClubView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;

    private String name;

    @Relation(Relation.Kind.MANY_TO_MANY)
    private List<MemberSubView> members;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<MemberSubView> getMembers() {
        return members;
    }

    public void setMembers(List<MemberSubView> members) {
        this.members = members;
    }
}
