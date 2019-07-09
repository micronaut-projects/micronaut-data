package example

import javax.persistence.*

@Entity
class Product {

    @Id
    @GeneratedValue
    Long id
    private String name
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Manufacturer manufacturer

    Product(String name, Manufacturer manufacturer) {
        this.name = name
        this.manufacturer = manufacturer
    }

    String getName() {
        return name
    }

    Manufacturer getManufacturer() {
        return manufacturer
    }
}
