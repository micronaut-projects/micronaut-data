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

    public Widget() {
    }

    public Widget(String name) {
        this.name = name;
    }

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
}
// end::uuid-widget[]
