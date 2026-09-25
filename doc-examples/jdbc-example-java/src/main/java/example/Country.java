package example;

import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.Set;
import java.util.UUID;

// tag::country[]
@MappedEntity
public class Country {

    @Id
    @AutoPopulated
    private UUID uuid;

    private String name;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "country")
    private Set<CountryRegion> regions;

    public Country(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Set<CountryRegion> getRegions() {
        return regions;
    }

    public void setRegions(Set<CountryRegion> regions) {
        this.regions = regions;
    }
}
// end::country[]
