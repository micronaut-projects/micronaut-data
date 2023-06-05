package io.micronaut.data.r2dbc.oraclexe.jsonview;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.JsonViewColumn;
import io.micronaut.data.tck.entities.Metadata;

import java.time.LocalDateTime;

@JsonView(value = "CONTACT_VIEW", alias = "cv", table = "TBL_CONTACT", permissions = "INSERT UPDATE DELETE")
public class ContactView {
    @Id
    private Long id;
    @JsonViewColumn(permissions = "UPDATE")
    private String name;
    private int age;
    @JsonViewColumn(permissions = "UPDATE")
    private LocalDateTime startDateTime;
    @JsonViewColumn(permissions = "UPDATE")
    private boolean active;
    @JsonProperty("_metadata")
    private Metadata metadata;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
