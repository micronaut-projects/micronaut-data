package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

// tag::lifecycle-entity[]
@MappedEntity
public class TimestampedRecord {
    @Id
    @GeneratedValue
    private String id;

    private String name;

    public TimestampedRecord() {
    }

    public TimestampedRecord(String name) {
        this.name = name;
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
}
// end::lifecycle-entity[]
