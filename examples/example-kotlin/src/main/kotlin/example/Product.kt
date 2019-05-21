package example

import javax.persistence.*

@Entity
data class Product(
    @Id
    @GeneratedValue
    var id: Long?,
    var name: String,
    @ManyToOne(optional = false)
    var manufacturer: Manufacturer
)
