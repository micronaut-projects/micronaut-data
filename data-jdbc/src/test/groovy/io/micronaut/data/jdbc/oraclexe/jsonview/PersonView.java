package io.micronaut.data.jdbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;

import java.util.Map;
import java.util.Objects;

@JsonView(entity = Person.class)
public class PersonView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String firstname;
    private String lastname;

    @JsonAnyGetter
    @JsonAnySetter
    private Map<String, Object> extras;

    public PersonView(String firstname, String lastname, Map<String, Object> extras) {
        this.firstname = firstname;
        this.lastname = lastname;
        this.extras = extras;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public Map<String, Object> getExtras() {
        return extras;
    }

    public void setExtras(Map<String, Object> extras) {
        this.extras = extras;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersonView that = (PersonView) o;
        return Objects.equals(id, that.id) && Objects.equals(firstname, that.firstname) && Objects.equals(lastname, that.lastname) && Objects.equals(extras, that.extras);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, firstname, lastname, extras);
    }
}
