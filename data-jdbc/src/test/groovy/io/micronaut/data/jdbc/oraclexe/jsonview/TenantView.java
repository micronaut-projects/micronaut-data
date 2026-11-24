package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@JsonView(entity = Tenant.class, alias = "tnw")
public class TenantView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;

    private String name;

    @Relation(Relation.Kind.MANY_TO_MANY)
    private List<ApartmentSubView> apartments;

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

    public List<ApartmentSubView> getApartments() {
        return apartments;
    }

    public void setApartments(List<ApartmentSubView> apartments) {
        this.apartments = apartments;
    }
}
