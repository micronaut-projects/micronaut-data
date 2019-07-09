package example

import javax.persistence.*

@Entity
class Sale {

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
