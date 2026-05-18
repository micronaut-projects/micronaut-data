package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@JsonView(entity = Building.class, alias = "bw")
public class BuildingView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;

    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<ApartmentSubView> apartments;

    public void setApartments(List<ApartmentSubView> apartments) {
        this.apartments = apartments;
    }

    public List<ApartmentSubView> getApartments() {
        return this.apartments;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
