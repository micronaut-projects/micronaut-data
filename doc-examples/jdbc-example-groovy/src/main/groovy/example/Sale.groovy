package example

import javax.persistence.Id
import javax.persistence.Entity
import javax.persistence.ManyToOne
import javax.persistence.GeneratedValue

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
