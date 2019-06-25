package io.micronaut.data.tck.entities;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.naming.NamingStrategies;

@MappedEntity(namingStrategy = NamingStrategies.Raw.class)
public class CountryRegion {

    @GeneratedValue
    @Id
    private Long id;
    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Country country;

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
}
