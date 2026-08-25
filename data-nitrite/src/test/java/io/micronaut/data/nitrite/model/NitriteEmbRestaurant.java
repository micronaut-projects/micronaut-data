package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

@MappedEntity("nitrite_emb_restaurant")
public class NitriteEmbRestaurant {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(value = Relation.Kind.EMBEDDED)
    private NitriteEmbAddress address;

    @Relation(value = Relation.Kind.EMBEDDED)
    private NitriteEmbAddress hqAddress;

    public NitriteEmbRestaurant() {
    }

    public NitriteEmbRestaurant(String name, NitriteEmbAddress address) {
        this.name = name;
        this.address = address;
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

    public NitriteEmbAddress getAddress() {
        return address;
    }

    public void setAddress(NitriteEmbAddress address) {
        this.address = address;
    }

    public NitriteEmbAddress getHqAddress() {
        return hqAddress;
    }

    public void setHqAddress(NitriteEmbAddress hqAddress) {
        this.hqAddress = hqAddress;
    }
}
