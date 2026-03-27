package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity("nitrite_mto_ref_a")
public class NitriteMtoRefA {
    @Id
    @GeneratedValue
    private String id;

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    private NitriteMtoRefB refB;

    public NitriteMtoRefA() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NitriteMtoRefB getRefB() {
        return refB;
    }

    public void setRefB(NitriteMtoRefB refB) {
        this.refB = refB;
    }
}
