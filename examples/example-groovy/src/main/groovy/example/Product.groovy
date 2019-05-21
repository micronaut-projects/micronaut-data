package example

import javax.persistence.*

@Entity
class Product {

    @Id
    @GeneratedValue
    Long id
    String name
    @ManyToOne(optional = false)
    Manufacturer manufacturer

    Product(String name, Manufacturer manufacturer) {
        this.name = name
        this.manufacturer = manufacturer
    }

    Product() {
    }
}
