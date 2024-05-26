
package example

import jakarta.persistence.*

@Entity
data class Product(
    @Id
    @GeneratedValue
    var id: Long?,
    var name: String,
    @ManyToOne
    var manufacturer: Manufacturer
)
