package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.List;

@MappedEntity("nitrite_product_option")
public class NitriteProductOption {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private NitriteProduct product;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "productOption", cascade = Relation.Cascade.ALL)
    private List<NitriteOption> option = new ArrayList<>();

    public NitriteProductOption() {
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

    public NitriteProduct getProduct() {
        return product;
    }

    public void setProduct(NitriteProduct product) {
        this.product = product;
    }

    public List<NitriteOption> getOption() {
        return option;
    }

    public void setOption(List<NitriteOption> option) {
        this.option = option;
    }
}
