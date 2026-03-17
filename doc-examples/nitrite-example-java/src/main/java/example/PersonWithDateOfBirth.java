package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.LocalDate;

// tag::personWithDateOfBirth[]
@MappedEntity
public class PersonWithDateOfBirth {
    @Id
    @GeneratedValue
    private String id;

    private String name;

    private LocalDate dateOfBirth;

    public PersonWithDateOfBirth() {
    }

    public PersonWithDateOfBirth(String name, LocalDate dateOfBirth) {
        this.name = name;
        this.dateOfBirth = dateOfBirth;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }
}
// end::personWithDateOfBirth[]
