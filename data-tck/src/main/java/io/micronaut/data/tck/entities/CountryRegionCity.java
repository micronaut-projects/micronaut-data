package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.naming.NamingStrategies;

@MappedEntity(namingStrategy = NamingStrategies.Raw.class)
public class CountryRegionCity {
    private final CountryRegion countryRegion;
    private final City city;

    public CountryRegionCity(CountryRegion countryRegion, City city) {
        this.countryRegion = countryRegion;
        this.city = city;
    }

    @Relation(Relation.Kind.MANY_TO_ONE)
    public CountryRegion getCountryRegion() {
        return countryRegion;
    }

    @Relation(Relation.Kind.MANY_TO_ONE)
    public City getCity() {
        return city;
    }
}
