package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonView

import java.time.LocalDateTime

// tag::record-example[]
@JsonView(value = "CONTACT_VIEW", alias = "cv", entity = Contact)
class ContactView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id
    String name
    int age
    LocalDateTime startDateTime
    boolean active
}
// end::record-example[]
