package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity("nitrite_oto_child")
public class NitriteOtoChild {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private NitriteOtoParent parent;

    public NitriteOtoChild() {
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

    public NitriteOtoParent getParent() {
        return parent;
    }

    public void setParent(NitriteOtoParent parent) {
        this.parent = parent;
    }
}
