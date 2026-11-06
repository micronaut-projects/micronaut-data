package io.micronaut.data.age.features;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.graph.annotation.Node;

@Node
@Indexes({
    @Index(columns = "email", unique = true),
    @Index(name = "account_location", columns = {"country", "city"})
})
public class Account {
    @Id
    @GeneratedValue
    private Long id;
    private String email;
    private String country;
    private String city;
    @Index(columns = "handle")
    private String handle;

    public Account(String email, String country, String city, String handle) {
        this.email = email;
        this.country = country;
        this.city = city;
        this.handle = handle;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public String getHandle() {
        return handle;
    }
}
