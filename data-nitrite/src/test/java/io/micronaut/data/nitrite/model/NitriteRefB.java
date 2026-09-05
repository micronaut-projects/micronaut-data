package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity("nitrite_ref_b")
public class NitriteRefB {
    @Id
    @GeneratedValue
    private String id;

    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.ALL)
    private NitriteRefC refC;

    public NitriteRefB() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NitriteRefC getRefC() {
        return refC;
    }

    public void setRefC(NitriteRefC refC) {
        this.refC = refC;
    }
}
