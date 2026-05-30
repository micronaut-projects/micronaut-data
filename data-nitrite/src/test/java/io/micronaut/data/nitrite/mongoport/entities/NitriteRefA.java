package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity("nitrite_ref_a")
public class NitriteRefA {
    @Id
    @GeneratedValue
    private String id;

    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
    private NitriteRefB refB;

    public NitriteRefA() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NitriteRefB getRefB() {
        return refB;
    }

    public void setRefB(NitriteRefB refB) {
        this.refB = refB;
    }
}
