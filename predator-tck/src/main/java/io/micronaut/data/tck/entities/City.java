package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.*;

@MappedEntity("T_CITY")
public class City {

    @Id
    @GeneratedValue
    private Long id;

    @MappedProperty("C_NAME")
    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private RegionOrCounty region;

    public City(String name, RegionOrCounty region) {
        this.name = name;
        this.region = region;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public RegionOrCounty getRegion() {
        return region;
    }
}
