
package example

import jakarta.persistence.*

@Entity
class Product {

    @Id
    @GeneratedValue
    Long id
    String name
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    Manufacturer manufacturer

    Product(String name, Manufacturer manufacturer) {
        this.name = name
        this.manufacturer = manufacturer
    }

    Product() {
    }
}
