package example

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Version

// tag::student[]
@Entity
class Student {

    @Id
    @GeneratedValue
    Long id
    @Version
    Long version
    // end::student[]

    String name
}
