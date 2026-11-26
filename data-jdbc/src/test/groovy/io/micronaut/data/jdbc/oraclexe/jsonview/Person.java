package io.micronaut.data.jdbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.naming.NamingStrategies;

import java.util.Map;

/**
 * It uses a different naming strategy than {@link PersonView}, so its flex column {@code extraFields} is the column
 * {@code extrafields}.
 */
@MappedEntity(value = "TBL_PERSON", alias = "p", namingStrategy = NamingStrategies.LowerCase.class)
public class Person {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String firstname;
    private String lastname;

    @JsonAnyGetter
    @JsonAnySetter
    private Map<String, Object> extraFields;

    public Person(String firstname, String lastname, Map<String, Object> extraFields) {
        this.firstname = firstname;
        this.lastname = lastname;
        this.extraFields = extraFields;
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

    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    public void setExtraFields(Map<String, Object> extraFields) {
        this.extraFields = extraFields;
    }
}
