package io.micronaut.data.runtime.criteria;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.search.SearchField;
import io.micronaut.data.annotation.search.Searchable;

/**
 * A searchable entity with an embedded property of the same name as a top-level search field, so a
 * boost passed to the criteria API has to be matched by its property path.
 */
@MappedEntity("place")
@Searchable
public class SearchPlace {

    @Id
    @GeneratedValue
    private Long id;
    @SearchField
    private String city;
    @Relation(Relation.Kind.EMBEDDED)
    private Address address;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    /**
     * The embedded address.
     */
    @Embeddable
    public static class Address {
        private String city;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }
}
