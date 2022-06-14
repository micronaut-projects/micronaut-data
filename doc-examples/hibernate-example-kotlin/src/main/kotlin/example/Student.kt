package example

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Version

// tag::student[]
@Entity
data class Student(
        @Id @GeneratedValue
        var id: Long?,
        @Version
        val version: Long,

        // end::student[]

        val name: String
)
