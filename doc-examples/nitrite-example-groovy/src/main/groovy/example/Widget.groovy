package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

import java.util.UUID

// tag::uuid-widget[]
@MappedEntity("widgets")
class Widget {
    @Id
    @GeneratedValue
    UUID id

    String name

    Widget() {
    }

    Widget(String name) {
        this.name = name
    }
}
// end::uuid-widget[]
