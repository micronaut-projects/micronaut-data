package example

import javax.persistence.*

@Entity
data class Sale(
    @Id
    @GeneratedValue
    var id: Long?,
    val product: Product,
    val quantity: Quantity
)