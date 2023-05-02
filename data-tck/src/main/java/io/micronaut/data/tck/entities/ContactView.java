package io.micronaut.data.tck.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity(value = "CONTACT_VIEW", alias = "cv")
@JsonView(table = "TBL_CONTACT")
public class ContactView {
    @Id
    private Long id;
    private String name;
    private int age;
    @JsonProperty("_metadata")
    private Metadata metadata;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
