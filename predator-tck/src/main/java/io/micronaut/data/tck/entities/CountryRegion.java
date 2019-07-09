package io.micronaut.data.tck.entities;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.naming.NamingStrategies;

import java.util.Set;

// tag::namingStrategy[]
@MappedEntity(namingStrategy = NamingStrategies.Raw.class)
public class CountryRegion {
// end::namingStrategy[]

    @GeneratedValue
    @Id
    private Long id;
    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Country country;

    @Relation(value = Relation.Kind.ONE_TO_MANY)
    private Set<City> cities;

    public CountryRegion(String name, @Nullable Country country) {
        this.name = name;
        this.country = country;
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

    public Country getCountry() {
        return country;
    }

    public Set<City> getCities() {
        return cities;
    }

    public void setCities(Set<City> cities) {
        this.cities = cities;
    }
}
