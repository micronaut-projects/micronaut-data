
package example

import jakarta.persistence.*

@Entity
data class Sale(
    @Id
    @GeneratedValue
    var id: Long?,
    @ManyToOne
    val product: Product,
    val quantity: Quantity
)