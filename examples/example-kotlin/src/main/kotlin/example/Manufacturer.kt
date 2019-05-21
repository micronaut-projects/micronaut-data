package example

import javax.persistence.*

@Entity
data class Manufacturer(
    @Id
    @GeneratedValue
    var id: Long?,
    var name: String
)
