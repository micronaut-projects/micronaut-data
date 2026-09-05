package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.util.UUID;

// tag::uuid-widget[]
@MappedEntity("widgets")
public class Widget {
    @Id
    @GeneratedValue
    private UUID id;

    private String name;
    // end::uuid-widget[]

    public Widget() {
    }

    // tag::uuid-widget[]
    public Widget(String name) {
        this.name = name;
    }
    // end::uuid-widget[]

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
// tag::uuid-widget[]
}
// end::uuid-widget[]
