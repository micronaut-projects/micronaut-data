package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::lifecycle-entity[]
@MappedEntity
class TimestampedRecord {
    @Id
    @GeneratedValue
    String id

    String name

    TimestampedRecord() {
    }

    TimestampedRecord(String name) {
        this.name = name
    }
}
// end::lifecycle-entity[]
