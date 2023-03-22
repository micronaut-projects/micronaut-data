package example

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Version

// tag::student[]
@Entity
data class Student(
        @Id @GeneratedValue
        var id: Long?,
        @Version
        val version: Long,

        // end::student[]

        var name: String
) {
    constructor(name: String) : this(null, 0, name)
}
