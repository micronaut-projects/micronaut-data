package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.List;

@MappedEntity("nitrite_oto_parent")
public class NitriteOtoParent {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.ALL)
    private List<NitriteOtoChild> children = new ArrayList<>();

    public NitriteOtoParent() {
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

    public List<NitriteOtoChild> getChildren() {
        return children;
    }

    public void setChildren(List<NitriteOtoChild> children) {
        this.children = children;
    }
}
