package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@MappedEntity("nitrite_category")
public class NitriteCategory {
    @Id
    @GeneratedValue
    private String id;
    private String name;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "category", cascade = Relation.Cascade.ALL)
    private List<NitriteProduct> productList;

    public NitriteCategory() {
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

    public List<NitriteProduct> getProductList() {
        return productList;
    }

    public void setProductList(List<NitriteProduct> productList) {
        this.productList = productList;
    }
}
