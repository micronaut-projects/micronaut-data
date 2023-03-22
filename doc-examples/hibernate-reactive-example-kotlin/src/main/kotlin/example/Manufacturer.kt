
package example

import jakarta.persistence.*

@Entity
data class Manufacturer(
    @Id
    @GeneratedValue
    var id: Long?,
    var name: String
)
