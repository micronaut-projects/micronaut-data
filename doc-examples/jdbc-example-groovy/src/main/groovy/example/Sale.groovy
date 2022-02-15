
package example

import jakarta.persistence.Id
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import jakarta.persistence.GeneratedValue

@Entity
class Sale {

    @ManyToOne
    final Product product

    final Quantity quantity

    @Id
    @GeneratedValue
    Long id

    Sale(Product product, Quantity quantity) {
        this.product = product
        this.quantity = quantity
    }
}
