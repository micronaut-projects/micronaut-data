package example

import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType

import javax.persistence.*

@Entity
class Sale {

    @ManyToOne
    final Product product
    @TypeDef(type = DataType.INTEGER)
    final Quantity quantity

    @Id
    @GeneratedValue
    Long id

    Sale(Product product, Quantity quantity) {
        this.product = product
        this.quantity = quantity
    }
}
