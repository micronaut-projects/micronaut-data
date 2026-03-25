package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@MappedEntity(value = "TBL_BUILDING", alias = "b")
public class Building {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<Apartment> apartments;

    public void setApartments(List<Apartment> apartments) {
        this.apartments = apartments;
    }

    public List<Apartment> getApartments() {
        return this.apartments;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}

