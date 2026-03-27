package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity("nitrite_mto_ref_b")
public class NitriteMtoRefB {
    @Id
    @GeneratedValue
    private String id;

    @Relation(value = Relation.Kind.MANY_TO_ONE, cascade = Relation.Cascade.ALL)
    private NitriteMtoRefC refC;

    public NitriteMtoRefB() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NitriteMtoRefC getRefC() {
        return refC;
    }

    public void setRefC(NitriteMtoRefC refC) {
        this.refC = refC;
    }
}
