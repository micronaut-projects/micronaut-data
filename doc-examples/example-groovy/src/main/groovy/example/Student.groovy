package example

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Version

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
