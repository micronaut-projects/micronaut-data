package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.naming.NamingStrategies;
import org.jspecify.annotations.Nullable;

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
// tag::namingStrategy[]
    // ...
}
// end::namingStrategy[]
